# Customer Service

Customer Service dengan Java 21, Spring Boot, MyBatis, MySQL, dan Hexagonal Architecture (Ports & Adapters). Scope: Customer, Customer Session, Customer Message. Tidak bergantung pada Store/Table (konsep milik Order Service).

## Arsitektur

```text
adapter.in.web  →  application (use cases + ports)  →  domain
                          ↑ implements
        adapter.out.persistence.mybatis (Mapper → XML → MySQL)
```

- `domain` — pure Java: model (`Customer`, `CustomerSession`, `CustomerMessage`), enum (`CustomerSessionStatus/Source/Role`), business exceptions. Tanpa Spring/MyBatis/Jackson.
- `application` — input port (use case interface + command record), output port (repository interface), service `@Transactional`. Hanya tahu domain + port.
- `adapter.in.web` — REST controller, DTO + Bean Validation, global exception handler. Tidak menyentuh MyBatis.
- `adapter.out.persistence.mybatis` — entity persistence, mapper interface + XML, repository adapter yang mengimplementasikan output port dan melakukan mapping entity ↔ domain.

## Menjalankan

Prasyarat: MySQL berjalan, database dibuat manual (Flyway hanya membuat tabel):

```sql
CREATE DATABASE `db-harmoni-auth`;
```

Kredensial terpusat pada placeholder `harmoni.user.db` / `harmoni.user.password` di `application.yaml`; koneksi utama memakai Apache Commons DBCP2:

```bash
./mvnw spring-boot:run
```

Migrasi schema dijalankan otomatis oleh Flyway saat startup:

- `V1__init_schema.sql` — schema awal (sesuai spesifikasi).
- `V2__customer_soft_delete.sql` — soft delete customer (lihat bawah).

## Keputusan Soft Delete

Delete customer memakai **soft delete**: percakapan (`messages` → `sessions` → `customers`) adalah riwayat bernilai bisnis; hard delete akan memutus FK chain atau menghapus history. Perubahan schema yang dilakukan (tidak diam-diam):

```sql
ALTER TABLE customers ADD COLUMN deleted_at timestamp NULL DEFAULT NULL AFTER email;
ALTER TABLE customers ADD KEY idx_customers_deleted_at (deleted_at);
```

Semua query read/update menambahkan `WHERE deleted_at IS NULL`; `DELETE` men-set `deleted_at = CURRENT_TIMESTAMP`. Trade-off: unique key `uk_customers_phone` tetap me-reserve phone customer terhapus — dup-check aplikasi sengaja konsisten termasuk row terhapus.

## API

| Method | Path | Keterangan |
|---|---|---|
| POST | `/api/v1/customers` | Create customer (201) |
| GET | `/api/v1/customers/{id}` | Detail customer |
| GET | `/api/v1/customers?search=&page=0&size=20` | Search name/phone/email, pagination di DB |
| PUT | `/api/v1/customers/{id}` | Update |
| DELETE | `/api/v1/customers/{id}` | Soft delete (204) |
| POST | `/api/v1/customer-sessions` | Create session; `customerId` boleh null (anonymous); token dibuat backend |
| GET | `/api/v1/customer-sessions/{id}` | Detail session |
| POST | `/api/v1/customer-sessions/{id}/close` | Close session (CLOSED tidak bisa close lagi / re-open) |
| POST | `/api/v1/customer-sessions/{sessionId}/messages` | Add message (role: USER/ASSISTANT/SYSTEM); ditolak jika session CLOSED |
| GET | `/api/v1/customer-sessions/{sessionId}/messages?page=0&size=50` | History ASC, pagination di DB |

Contoh error response:

```json
{
  "timestamp": "2026-08-23T06:00:00Z",
  "status": 404,
  "code": "CUSTOMER_NOT_FOUND",
  "message": "Customer not found: 42"
}
```

Kode error: `CUSTOMER_NOT_FOUND` (404), `CUSTOMER_SESSION_NOT_FOUND` (404), `DUPLICATE_CUSTOMER` (409), `INVALID_CUSTOMER_SESSION` (400), `INVALID_CUSTOMER_MESSAGE` (400), `VALIDATION_ERROR` (400), `INTERNAL_ERROR` (500 tanpa stack trace).

## Security

Tidak ada auth di service ini — autentikasi diasumsikan ditangani API Gateway; service hanya menerima request terautentikasi. Extension point authorization dapat ditambahkan nanti sebagai filter/interceptor di `adapter.in.web` tanpa mengubah domain/application.

## Testing

```bash
mvn test
```

- Unit test application service (Mockito): create/update/not-found/duplicate phone, anonymous & identified session, close session, message pada session CLOSED, role invalid, clamping pagination.
- Controller test `@WebMvcTest`: seluruh endpoint + validasi + mapping error.
- Repository integration test `@MybatisTest` + H2 MODE=MySQL (Docker tidak tersedia di lingkungan ini): CRUD, search & pagination di SQL, soft delete, FK message→session, ordering conversation.
- Full context smoke test `@SpringBootTest`: wiring end-to-end create customer → get → create session.

Untuk integration test terhadap MySQL asli (mis. Testcontainers), jalankan dengan profile datasource MySQL dan Flyway aktif.
