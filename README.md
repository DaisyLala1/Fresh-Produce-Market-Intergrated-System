# Fresh Produce Market POS and Consignment Management System

Web application implementing the design in ISJ107V Assignment 3: consignment management with unique
reference numbers, POS sales with **FIFO** stock allocation, inventory and stock adjustments, configurable
agent commissions, reports, role-based access for **Agents** and **Administrators**, and an
**AI sales-forecasting** service (Python, scikit-learn, FastAPI).

```
fresh-produce-pos/
├── backend/                     Spring Boot application (Java 17) – REST API + web front-end
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/freshproduce/pos/
│       │   ├── domain/          JPA entities (User, Farmer, Product, Consignment, Sale, SaleItem,
│       │   │                    ConsignmentAllocation, StockMovement, CommissionRate, Commission, SalesForecast)
│       │   ├── repository/      Spring Data repositories (FIFO query with row locking)
│       │   ├── service/         Business logic (SaleService = POS + FIFO, ForecastService = AI client, …)
│       │   ├── controller/      REST endpoints under /api/**
│       │   ├── security/        Login, CSRF handling
│       │   ├── config/          SecurityConfig (RBAC rules), DataSeeder (demo data)
│       │   ├── dto/             Request / response records with validation rules
│       │   └── exception/       Consistent JSON error responses
│       ├── main/resources/
│       │   ├── application.properties       MySQL configuration (default)
│       │   ├── application-h2.properties    In-memory database for quick demos
│       │   └── static/          Front-end: HTML, CSS, JavaScript (Bootstrap 5 + Chart.js bundled locally)
│       └── test/                JUnit tests mapped to the documented test cases
├── ai-service/                  Python + FastAPI forecasting service
│   ├── app/main.py              POST /api/forecast, GET /health
│   ├── app/model.py             Random Forest model, MAE evaluation vs. baseline
│   ├── data/                    Reference sales dataset + generator script
│   ├── evaluate.py              AI-01 evidence: model MAE vs. baseline MAE for every product
│   └── tests/test_api.py        pytest tests for AI-01, AI-02, AI-03
└── database/schema.sql          MySQL schema (reference; tables are created automatically)
```

---

## 1. Requirements

| Software | Version |
|---|---|
| Java JDK | 17 or later |
| Maven | 3.9+ (or use the Maven bundled with IntelliJ IDEA / NetBeans) |
| MySQL | 8.x (optional for a quick demo – see the H2 option) |
| Python | 3.10 or later |
| Browser | Any modern browser (Chrome, Edge, Firefox) |

Minimum hardware: dual-core 2.0 GHz CPU, 8 GB RAM, 256 GB SSD. No GPU is needed.

## 2. Start the AI forecasting service

```bash
cd ai-service
python -m venv .venv
# Windows: .venv\Scripts\activate      macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

Check it at http://localhost:8000/health and see the interactive API docs at http://localhost:8000/docs.

## 3. Start the main application

**Option A – quick demo (no MySQL needed, data is kept in memory):**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

**Option B – MySQL (default):**

1. Make sure MySQL is running. The database `fresh_produce_pos` is created automatically.
2. Set your MySQL credentials (defaults are `root` / `root`):
   ```bash
   # Windows PowerShell
   $env:DB_USERNAME="root"; $env:DB_PASSWORD="your-password"
   # macOS / Linux
   export DB_USERNAME=root DB_PASSWORD=your-password
   ```
3. Run:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

Open **http://localhost:8080** and log in:

| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `Admin@123` |
| Agent | `agent1` | `Agent@123` |
| Agent | `agent2` | `Agent@123` |

On the first start with an empty database the system loads demonstration data: 12 products, 5 farmers,
3 consignments per product and about 90 days of sales history (processed through the real POS/FIFO logic),
so reports and AI forecasts work immediately. Turn this off with `SEED_DATA=false`.

To build a runnable JAR: `mvn clean package` then `java -jar target/fresh-produce-pos-1.0.0.jar`.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/fresh_produce_pos?...` | MySQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | MySQL credentials |
| `AI_SERVICE_URL` | `http://localhost:8000` | Where the FastAPI service runs |
| `AI_API_KEY` | `fresh-produce-shared-key` | Shared secret between Spring Boot and FastAPI – **set the same value for both** in production |
| `SEED_DATA` | `true` | Load demo data on an empty database |
| `PORT` | `8080` | Web application port |

## 4. Running the tests

```bash
cd backend && mvn test            # JUnit: FIFO, commission, rollback, RBAC, login
cd ai-service && pytest -q        # AI-01 accuracy, AI-02 response time, AI-03 validation
cd ai-service && python evaluate.py   # MAE of the model vs. baseline for every product
```

## 5. How the requirements are implemented

| Req. | Where | Test |
|---|---|---|
| FR-01 Register farmers, record consignments with unique reference | `FarmerService`, `ConsignmentService.record` (reference `CON-YYYY-NNNNN`) | `tc01_recordConsignmentAssignsUniqueReference` |
| FR-02 POS sales with FIFO allocation | `SaleService.processSale` / `allocateFifo`, `ConsignmentRepository.findEligibleForFifo` | `tc02_fifoDeductsFromOldestConsignmentFirst` |
| FR-03 Balances update, transaction history | `Consignment.deduct`, `StockMovement`, consignment detail page | `tc03_balanceUpdatesAfterSaleAndHistoryIsRecorded` |
| FR-04 Commission + reports | `CommissionService.calculate`, `ReportService` | `tc04_…`, `CommissionCalculationTest` |
| FR-05 Display AI forecasts | `ForecastService` → FastAPI, Forecasting page | `test_tc05_valid_request_returns_forecast` |
| FR-06 Login with role-based access | `AuthController`, `SecurityConfig` | `SecurityAccessTest` |
| FR-07 Manage products (Administrator) | `ProductService`, Products page | `agentCannotCreateProductsDirectlyThroughApi` |
| FR-08 Stock adjustments (no sale created) | `InventoryService.recordAdjustment` | `tc08_stockAdjustmentReducesBalanceWithoutCreatingSale` |
| FR-09 Search consignment by reference | `GET /api/consignments/search?reference=` and the top search bar | `tc09_searchConsignmentByReference` |
| FR-10 Configure commission rate | `CommissionService.updateRate` | `tc10_newCommissionRateAppliesToSubsequentSales` |
| NFR-02 RBAC also on direct URL/API | URL rules in `SecurityConfig` + `@PreAuthorize` | `SecurityAccessTest` |
| NFR-03 Sale = single DB transaction | `@Transactional` on `SaleService.processSale` | `rt01_failureDuringSaleRollsBackEverything` |
| AI-01 Lower MAE than baseline | `model.evaluate` (hold-out vs. seasonal-naive baseline) | `test_ai01_…`, `evaluate.py` |
| AI-02 Forecast within 5 seconds | Direct (non-recursive) model, timeouts in `ForecastService` | `test_ai02_response_within_five_seconds` |
| AI-03 Input validation | Pydantic schema in `app/main.py`; Bean Validation in `ForecastRequest` | `test_ai03_*` |

### Main API endpoints

| Method | Endpoint | Who |
|---|---|---|
| POST | `/api/auth/login`, `/api/auth/logout` · GET `/api/auth/me` | everyone |
| GET/POST/PUT | `/api/farmers` (PATCH `/{id}/status` = Administrator) | Agent, Administrator |
| GET · POST/PUT/PATCH | `/api/products` | read: all · change: Administrator |
| GET/POST | `/api/consignments`, `/api/consignments/{id}`, `/api/consignments/search?reference=` | Agent, Administrator |
| POST/GET | `/api/sales`, `/api/sales/{id}` | Agent, Administrator |
| GET/POST | `/api/inventory`, `/api/inventory/movements`, `/api/inventory/adjustments` | Agent, Administrator |
| GET · PUT | `/api/commission-rates`, `/api/commission-rates/current` | read: all · change: Administrator |
| GET | `/api/reports/sales`, `/consignments`, `/inventory`, `/commissions` (`?from=&to=`) | Agent, Administrator |
| POST/GET | `/api/forecast`, `/api/forecast/history` | Agent, Administrator |
| GET/POST/PUT | `/api/users` | Administrator |

All POST/PUT/PATCH requests need the CSRF token: the browser front-end handles this automatically
(cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`). In Postman, first call `GET /api/auth/csrf`, then copy the
`XSRF-TOKEN` cookie value into an `X-XSRF-TOKEN` header.

**AI service** – `POST http://localhost:8000/api/forecast` with header `X-API-Key`:

```json
{ "productId": 101, "productName": "Tomatoes", "forecastPeriodDays": 7,
  "history": [ { "date": "2026-09-01", "quantity": 58 }, … ] }
```
returns `{ "productId": 101, "predictedQuantity": 420.0, "dailyForecast": [...], "mae": 4.5, "baselineMae": 5.9, … }`.
Invalid input (period ≤ 0 or > 30, product ID ≤ 0, negative quantities, missing fields) returns HTTP 422.

## 6. Security notes

* Passwords are stored as BCrypt hashes; the plain password is never saved.
* Sessions use HTTP-only cookies; the session ID changes on login; CSRF protection is on.
* Administrator-only functions are blocked at the API level, not just hidden in the menu.
* The AI service only accepts requests carrying the shared API key and only receives product IDs,
  names and daily quantities – no personal data.
* Change `AI_API_KEY` and the demo passwords before deploying, and serve the site over HTTPS.

## 7. Troubleshooting

| Problem | Fix |
|---|---|
| "Communications link failure" on start-up | MySQL is not running or the credentials are wrong – check `DB_USERNAME` / `DB_PASSWORD`, or use the H2 option. |
| Forecast page says the AI service is unavailable | Start the FastAPI service (section 2) and check `AI_SERVICE_URL`. POS and inventory keep working without it. |
| "Insufficient stock" at the POS | The requested quantity is more than the eligible stock. Record a new consignment or reduce the quantity. |
| "Your session token has expired" | Refresh the page (the CSRF cookie is renewed) and try again. |
| Port 8080 or 8000 already in use | Set `PORT=8081` for Spring Boot, or `uvicorn app.main:app --port 8001` and `AI_SERVICE_URL=http://localhost:8001`. |
