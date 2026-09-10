package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.application.port.in.ChatWithAiUseCase;
import com.harmoni.pos.customer.application.port.out.CustomerMessageRepository;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.config.AiProperties;
import com.harmoni.pos.customer.config.RetryProperties;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;
import com.harmoni.pos.customer.ai.tool.CartTools;
import com.harmoni.pos.customer.ai.tool.MenuTools;
import com.harmoni.pos.customer.ai.tool.OrderTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements {@link ChatWithAiUseCase}: validates the session, persists the USER message, calls the
 * configured AI provider via the Spring AI {@link ChatClient} (OpenAI-compatible, so groq / Ollama /
 * gemini profiles all work), persists the ASSISTANT reply, and exposes blocking, sync, and streaming
 * variants.
 * <p>
 * Tool calling is wired through {@link MenuTools}, {@link CartTools} and {@link OrderTools}; RAG
 * retrieval is optional ({@code harmoni.ai.rag-enabled}, off by default — tools are the source of
 * truth). Models that emit raw tool JSON as text instead of using tool binding are intercepted and
 * executed directly so the persisted reply never leaks JSON.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAiChatService implements ChatWithAiUseCase {

    private final ChatClient chatClient;
    private final CustomerSessionRepository sessionRepository;
    private final CustomerMessageRepository messageRepository;
    private final VectorStore vectorStore;
    private final MenuTools menuTools;
    private final CartTools cartTools;
    private final OrderTools orderTools;
    private final AiProperties aiProps;
    private final RetryProperties retryProps;

    private static final Pattern TOOL_JSON_PATTERN = Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"(getProductsByCategory|searchProductsByName|searchMenu|searchCategoriesByName|getCategoriesByBrand|addToOrder|getCurrentOrder|removeOrderItem|confirmOrder)\"[^}]*\\}");
    private static final Pattern CATEGORY_ID_PATTERN = Pattern.compile("\"categoryId\"\\s*:\\s*(\\d+)");
    private static final Pattern SEARCH_NAME_PATTERN = Pattern.compile("\"(productName|categoryName|keyword)\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Blocking chat: validates the session, persists the USER message, calls the AI provider with
     * rate-limit retry, executes leaked tool JSON, then persists and returns the ASSISTANT reply.
     * The LLM connection originates from this service (not from the server that calls us).
     */
    @Override
    @Transactional
    public CustomerMessage chat(long sessionId, String userMessage) {
        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomerSessionNotFoundException(sessionId));
        if (!session.isOpen()) {
            throw new InvalidCustomerMessageException("Cannot chat on closed session " + sessionId);
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new InvalidCustomerMessageException("Message content is required");
        }

        // 1. Persist USER message (same as AddCustomerMessageUseCase)
        CustomerMessage userMsg = messageRepository.save(
                CustomerMessage.create(sessionId, CustomerMessageRole.USER, userMessage));
        log.info("Saved USER message sessionId={} id={}", sessionId, userMsg.getId());

        // 2. Call Ollama via Spring AI ChatClient — connection originates FROM customer service
        String assistantText;
        if (!aiProps.isEnabled()) {
            assistantText = "AI disabled (harmoni.ai.enabled=%s). Echo: %s".formatted(aiProps.isEnabled(), userMessage);
        } else {
            try {
                // RAG: retrieve relevant menu docs (SimpleVectorStore) — disabled (no RAG embeddings configured)
                String ragContext = "";
if (aiProps.isRagEnabled()) {
                    try {
                        var docs = vectorStore.similaritySearch(SearchRequest.builder()
                                .query(userMessage).topK(3).similarityThreshold(0.6).build());
                        if (docs != null && !docs.isEmpty()) {
                            ragContext = docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
                            log.info("RAG hit sessionId={} docs={}", sessionId, docs.size());
                        }
                    } catch (Exception e) {
                        log.warn("RAG search failed sessionId={}: {}", sessionId, e.getMessage());
                    }
                }
                String augmented = "[customerSessionId=" + sessionId + "] " + userMessage;
                if (!ragContext.isBlank()) {
                    augmented = "Context:\n" + ragContext + "\n\nUser: " + augmented;
                }
                assistantText = callWithRetry(augmented, sessionId);
                if (assistantText == null || assistantText.isBlank()) {
                    assistantText = "Maaf, saya tidak bisa menjawab saat ini.";
                }
                // Handle leaked tool JSON when model is qwen2.5-coder / qwen3 (coder/thinking) that doesn't use Spring AI tool binding
                String leakedHandled = handleLeakedToolJson(assistantText, sessionId);
                String rawForLog = assistantText.substring(0, Math.min(120, assistantText.length()));
                if (leakedHandled != null) {
                    log.warn("Detected leaked tool JSON sessionId={} raw='{}' -> handled via MenuTools", sessionId, rawForLog);
                    assistantText = leakedHandled;
                } else {
                    assistantText = cleanAssistantText(assistantText);
                }
                log.info("AI response sessionId={} chars={}", sessionId, assistantText.length());
            } catch (Exception e) {
                log.error("AI call failed sessionId={}: {}", sessionId, e.getMessage(), e);
                assistantText = toUserFriendlyError(e, userMessage);
            }
        }

        // 3. Persist ASSISTANT message and return it
        CustomerMessage assistantMsg = messageRepository.save(
                CustomerMessage.create(sessionId, CustomerMessageRole.ASSISTANT, assistantText));
        log.info("Saved ASSISTANT message sessionId={} id={}", sessionId, assistantMsg.getId());
        return assistantMsg;
    }

    /**
     * Convenience wrapper: runs {@link #chat(long, String)} and returns only the assistant text.
     */
    @Override
    public String chatSync(long sessionId, String userMessage) {
        return chat(sessionId, userMessage).getMessage();
    }

    /**
     * Streaming variant of {@link #chat(long, String)}: persists the USER message, then emits the
     * cleaned assistant text as a single chunk (rate-limit retries handled in-band).
     */
    @Override
    public Flux<String> chatStream(long sessionId, String userMessage) {
        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomerSessionNotFoundException(sessionId));
        if (!session.isOpen()) {
            throw new InvalidCustomerMessageException("Cannot chat on closed session " + sessionId);
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new InvalidCustomerMessageException("Message content is required");
        }

        // Persist USER immediately (same as blocking chat)
        CustomerMessage userMsg = messageRepository.save(
                CustomerMessage.create(sessionId, CustomerMessageRole.USER, userMessage));
        log.info("Saved USER (stream) sessionId={} id={}", sessionId, userMsg.getId());

        if (!aiProps.isEnabled()) {
            String echo = "AI disabled (harmoni.ai.enabled=%s). Echo: %s".formatted(aiProps.isEnabled(), userMessage);
            CustomerMessage assistantMsg = messageRepository.save(
                    CustomerMessage.create(sessionId, CustomerMessageRole.ASSISTANT, echo));
            log.info("Saved ASSISTANT (ai disabled, stream) sessionId={} id={}", sessionId, assistantMsg.getId());
            return Flux.just(echo);
        }

        // RAG context — disabled (tools are the source of truth)
        String ragContext = "";
        if (aiProps.isRagEnabled()) {
            try {
                var docs = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(userMessage).topK(3).similarityThreshold(0.6).build());
                if (docs != null && !docs.isEmpty()) {
                    ragContext = docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
                    log.info("RAG hit (stream) sessionId={} docs={}", sessionId, docs.size());
                }
            } catch (Exception e) {
                log.warn("RAG search failed (stream) sessionId={}: {}", sessionId, e.getMessage());
            }
        }
        String augmented = "[customerSessionId=" + sessionId + "] " + userMessage;
        if (!ragContext.isBlank()) {
            augmented = "Context:\n" + ragContext + "\n\nUser: " + augmented;
        }

        StringBuilder fullResponse = new StringBuilder();
        // Collect raw chunks, then handle leaked tool JSON before emitting to ai-order
        // This fixes qwen3:4b/8b thinking models that emit raw {"name":"getCategoriesByBrand"...} as text
        // instead of via Spring AI tool binding. We intercept and execute via MenuTools/CartTools/OrderTools.
        Flux<String> rawFlux = chatClient.prompt()
                .user(augmented)
                .advisors(advisor -> advisor.param("chat_memory_conversation_id", String.valueOf(sessionId)))
                .stream()
                .content()
                .doOnNext(chunk -> Optional.ofNullable(chunk).ifPresent(fullResponse::append))
                .retryWhen(Retry.backoff(retryProps.getMaxAttempts() - 1, retryProps.getInitialInterval())
                        .maxBackoff(retryProps.getMaxInterval())
                        .jitter(retryProps.getJitterFactor())
                        .filter(this::isRateLimit)
                        .doBeforeRetry(sig -> log.warn("AI provider 429 stream retry {}/{} sessionId={} cause={}", sig.totalRetries() + 1, retryProps.getMaxAttempts(), sessionId, sig.failure().getMessage()))
                        .onRetryExhaustedThrow((spec, sig) -> sig.failure()));

        return rawFlux.collectList().flatMapMany(list -> {
            String full = fullResponse.toString();
            String leakedHandled = handleLeakedToolJson(full, sessionId);
            String finalText = Optional.ofNullable(leakedHandled)
                    .map(lh -> {
                        log.warn("Detected leaked tool JSON (stream) sessionId={} raw='{}'", sessionId, full.substring(0, Math.min(120, full.length())));
                        return cleanAssistantText(lh);
                    })
                    .orElseGet(() -> cleanAssistantText(full));
            if (finalText == null || finalText.isBlank()) {
                finalText = "Maaf, saya tidak bisa menjawab saat ini.";
            }
            // Persist handled text
            try {
                CustomerMessage assistantMsg = messageRepository.save(
                        CustomerMessage.create(sessionId, CustomerMessageRole.ASSISTANT, finalText));
                log.info("Saved ASSISTANT (stream) sessionId={} id={} chars={}", sessionId, assistantMsg.getId(), finalText.length());
            } catch (Exception e) {
                log.error("Failed to persist ASSISTANT (stream) sessionId={}: {}", sessionId, e.getMessage(), e);
            }
            // Emit handled text as single chunk to ai-order (no typewriter, no leaked JSON)
            // Split into words for frontend that expects streaming, but keep spaces
            return Flux.just(finalText);
        })
                .doOnError(e -> log.error("AI stream failed sessionId={}: {}", sessionId, e.getMessage(), e))
                .onErrorResume(e -> {
                    String fallback = toUserFriendlyError(e, userMessage);
                    try {
                        messageRepository.save(CustomerMessage.create(sessionId, CustomerMessageRole.ASSISTANT, fallback));
                    } catch (Exception ex) {
                        log.error("Failed to persist fallback ASSISTANT sessionId={}: {}", sessionId, ex.getMessage(), ex);
                    }
                    return Flux.just(fallback);
                });
    }

    private String callWithRetry(String augmented, long sessionId) {
        Exception last = null;
        Duration currentInterval = retryProps.getInitialInterval();
        for (int attempt = 1; attempt <= retryProps.getMaxAttempts(); attempt++) {
            try {
                String out = chatClient.prompt()
                        .user(augmented)
                        .advisors(advisor -> advisor.param("chat_memory_conversation_id", String.valueOf(sessionId)))
                        .call()
                        .content();
                if (attempt > 1) log.info("AI provider retry success attempt {}/{} sessionId={}", attempt, retryProps.getMaxAttempts(), sessionId);
                return out;
            } catch (Exception e) {
                last = e;
                if (isRateLimit(e) && attempt < retryProps.getMaxAttempts()) {
                    // Add jitter to prevent thundering herd
                    long jitter = (long) (currentInterval.toMillis() * retryProps.getJitterFactor() * (Math.random() * 2 - 1));
                    long wait = Math.max(0, currentInterval.toMillis() + jitter);
                    log.warn("AI provider 429 rate limit sessionId={} attempt {}/{} — retry in {}ms: {}", sessionId, attempt, retryProps.getMaxAttempts(), wait, e.getMessage());
                    try { Thread.sleep(wait); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    // Exponential backoff with cap
                    currentInterval = Duration.ofMillis(Math.min((long) (currentInterval.toMillis() * retryProps.getMultiplier()), retryProps.getMaxInterval().toMillis()));
                    continue;
                }
                throw e;
            }
        }
        throw new RuntimeException(last);
    }

    private boolean isRateLimit(Throwable e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("rate_limit")) return true;
        // WebClientResponseException$TooManyRequests
        if (e.getClass().getSimpleName().contains("TooManyRequests")) return true;
        Throwable cause = e.getCause();
        if (cause != null && cause != e) return isRateLimit(cause);
        return false;
    }

    private String toUserFriendlyError(Throwable e, String userMessage) {
        if (isRateLimit(e)) {
            // Friendly, actionable — no raw 429 dump
            return "Maaf, permintaan sedang tinggi (batas pemakaian AI gratis tercapai). Silakan tunggu sebentar lalu coba lagi.\n\n"
                    + "Sementara itu kamu bisa ketik \"menu\" untuk melihat kategori tanpa AI, atau ulangi: \"" + (userMessage != null ? userMessage.substring(0, Math.min(40, userMessage.length())) : "") + "\"";
        }
        String raw = e.getMessage() != null ? e.getMessage() : e.toString();
        // Hide technical provider URL details
        if (raw.length() > 200) raw = raw.substring(0, 200);
        return "Maaf, AI sedang tidak tersedia: " + raw;
    }

private String cleanAssistantText(String text) {
        return Optional.ofNullable(text)
                .map(t -> t.replaceAll("(?s)thinking.*?done", "").trim())
                .map(t -> t.replaceAll("(?s)<\\|channel\\|>analysis<\\|message\\|>.*?<\\|end\\|>\\s*<\\|start\\|>assistant<\\|channel\\|>final<\\|message\\|>", "").trim())
                .map(t -> t.replaceAll("(?s)<\\|channel\\|>analysis<\\|message\\|>.*?<\\|end\\|>", "").trim())
                .map(t -> t.replaceAll("([,;:])(?=[^\\s])", "$1 "))
                .map(t -> TOOL_JSON_PATTERN.matcher(t).find()
                        ? TOOL_JSON_PATTERN.matcher(t).replaceAll("").trim()
                        : t)
                .filter(t -> !t.isBlank())
                .orElse("Maaf, saya sedang memproses permintaan Anda. Silakan coba lagi.");
    }

    private String handleLeakedToolJson(String raw, long sessionId) {
        if (raw == null) return null;
        Matcher m = TOOL_JSON_PATTERN.matcher(raw);
        if (!m.find()) return null;

        String json = m.group();
        log.info("Handling leaked tool JSON sessionId={} json={}", sessionId, json);

        if (json.contains("getProductsByCategory")) {
            Matcher cm = CATEGORY_ID_PATTERN.matcher(json);
            return cm.find()
                    ? "Berikut produk untuk kategori tersebut:\n" + menuTools.getProductsByCategory(Integer.parseInt(cm.group(1)))
                    : cleanAssistantText(raw.replace(json, "").trim());
        }
        if (json.contains("searchProductsByName") || json.contains("searchMenu")) {
            Matcher sm = SEARCH_NAME_PATTERN.matcher(json);
            return sm.find()
                    ? menuTools.searchProductsByName(sm.group(2))
                    : cleanAssistantText(raw.replace(json, "").trim());
        }
        if (json.contains("searchCategoriesByName")) {
            Matcher sm = SEARCH_NAME_PATTERN.matcher(json);
            return sm.find()
                    ? menuTools.searchCategoriesByName(sm.group(2))
                    : cleanAssistantText(raw.replace(json, "").trim());
        }
        if (json.contains("getCategoriesByBrand")) {
            return menuTools.getCategoriesByBrand(1);
        }
        if (json.contains("addToOrder")) {
            log.warn("Leaked addToOrder ignored sessionId={}", sessionId);
            return cleanAssistantText(raw.replace(json, "").trim());
        }
        if (json.contains("confirmOrder") || json.contains("getCurrentOrder") || json.contains("removeOrderItem")) {
            log.warn("Leaked cart/order tool ignored sessionId={} json={}", sessionId, json);
            return cleanAssistantText(raw.replace(json, "").trim());
        }
        return cleanAssistantText(raw.replace(json, "").trim());
    }
}
