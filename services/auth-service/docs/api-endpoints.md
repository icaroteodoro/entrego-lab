# 📡 Especificação de Endpoints REST - Auth Service

Esta documentação provê a referência completa de todos os endpoints expostos pelo **Auth Service**, contendo métodos HTTP, regras de autorização, contratos de entrada e saída (JSON), códigos de status HTTP e exemplos práticos de consumo com `curl`.

---

## 1. Visão Geral da API

- **URL Base Local**: `http://localhost:8081`
- **Prefixo dos Endpoints**: `/auth`
- **Padrão de Autenticação**: Bearer Token (JWT RFC 7519)
- **Content-Type**: `application/json`

### Tabela Resumo dos Endpoints

| Método | Endpoint | Proteção / Autorização | Descrição |
| :--- | :--- | :--- | :--- |
| `POST` | `/auth/register` | Pública (`permitAll`) | Cadastra um novo usuário com papel padrão `CUSTOMER`. |
| `POST` | `/auth/login` | Pública (`permitAll`) | Autentica com e-mail e senha, retornando par de tokens. |
| `POST` | `/auth/refresh` | Pública (`permitAll`) | Renova a sessão usando o refresh token (com rotação atômica). |
| `POST` | `/auth/logout` | Pública (`permitAll`) | Revoga o refresh token informado. |
| `GET` | `/auth/me` | Autenticado (`Bearer Token`) | Retorna os dados do perfil do usuário autenticado no token. |
| `GET` | `/auth/customer-role-test` | Role `CUSTOMER` | Endpoint de teste para validação do papel `CUSTOMER`. |
| `GET` | `/auth/admin-role-test` | Role `ADMIN` | Endpoint de teste para validação do papel `ADMIN`. |
| `GET` | `/actuator/health` | Pública (`permitAll`) | Health check da aplicação e liveness/readiness probes para Docker/K8s. |
| `GET` | `/actuator/info` | Pública (`permitAll`) | Informações gerais do microsserviço. |

---

## 2. Detalhamento dos Endpoints

### 2.1. `POST /auth/register`
Cadastra um novo usuário no sistema. Por padrão de negócio, todo usuário registrado por esta rota recebe automaticamente o papel `CUSTOMER` e status `ACTIVE`.

#### Headers
```http
Content-Type: application/json
```

#### Corpo da Requisição (`CreateUserRequestDTO`)
| Campo | Tipo | Obrigatório | Regras de Validação | Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `name` | String | Sim | `@NotBlank`, `@Size(max = 120)` | Nome completo do usuário. |
| `email` | String | Sim | `@NotBlank`, `@Email` | Endereço de e-mail único. |
| `password` | String | Sim | `@NotBlank`, `@Size(min = 8, max = 100)` | Senha em texto plano (mínimo 8 caracteres). |

**Exemplo de Payload de Envio:**
```json
{
  "name": "Maria Silva",
  "email": "maria.silva@example.com",
  "password": "senhaForte123@"
}
```

#### Respostas

- **Status `200 OK`**: Usuário cadastrado com sucesso.

##### Corpo da Resposta (`UserResponseDTO`)
| Campo | Tipo | Descrição |
| :--- | :--- | :--- |
| `id` | UUID | Identificador único gerado para a conta do usuário. |
| `name` | String | Nome do usuário. |
| `email` | String | E-mail do usuário. |

**Exemplo de Payload de Retorno:**
```json
{
  "id": "e2a3b174-8b89-42b7-8ceb-b6d8b99c011e",
  "name": "Maria Silva",
  "email": "maria.silva@example.com"
}
```

- **Status `400 Bad Request`**: Dados de entrada inválidos (Bean Validation).
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "password: size must be between 8 and 100",
  "path": "/auth/register",
  "timestamp": "2026-09-26T17:15:30.123"
}
```

#### Exemplo cURL
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Maria Silva",
    "email": "maria.silva@example.com",
    "password": "senhaForte123@"
  }'
```

---

### 2.2. `POST /auth/login`
Autentica o usuário no sistema verificando suas credenciais (e-mail case-insensitive e senha com BCrypt). Se as credenciais forem válidas e a conta estiver ativa, emite um Access Token JWT e um Refresh Token opaco.

#### Headers
```http
Content-Type: application/json
```

#### Corpo da Requisição (`LoginRequestDTO`)
| Campo | Tipo | Obrigatório | Validações | Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `email` | String | Sim | `@NotBlank`, `@Email` | E-mail da conta. |
| `password` | String | Sim | `@NotBlank` | Senha cadastrada. |

**Exemplo de Payload de Envio:**
```json
{
  "email": "maria.silva@example.com",
  "password": "senhaForte123@"
}
```

#### Respostas

- **Status `200 OK`**: Autenticação bem-sucedida.
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJlbnRyZWdvLWF1dGgiLCJzdWIiOiJlMmEzYjE3NC04Yjg5LTQyYjctOGNlYi1iNmQ4Yjk5YzAxMWUiLCJpYXQiOjE3NzQ2NTI0MDAsImV4cCI6MTc3NDY1MzMwMCwicm9sZXMiOlsiQ1VTVE9NRVIiXX0...",
  "refreshToken": "U7c8q1P_zT4K0xN8w2L9aBv7c3D1eF5gH2jK4lM6nO8",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

- **Status `401 Unauthorized`**: E-mail não encontrado ou senha incorreta.
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/auth/login",
  "timestamp": "2026-09-26T17:16:00.456"
}
```

- **Status `403 Forbidden`**: Conta com status diferente de `ACTIVE` (ex: `BLOCKED`, `INACTIVE`).
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "User account is not active",
  "path": "/auth/login",
  "timestamp": "2026-09-26T17:16:05.789"
}
```

#### Exemplo cURL
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "maria.silva@example.com",
    "password": "senhaForte123@"
  }'
```

---

### 2.3. `POST /auth/refresh`
Renova a sessão autenticada. Recebe o refresh token ativo do cliente, revoga-o atomicamente (Refresh Token Rotation - RTR), verifica se o usuário continua ativo, e emite um **novo par** de Access Token e Refresh Token.

#### Headers
```http
Content-Type: application/json
```

#### Corpo da Requisição (`RefreshTokenRequestDTO`)
| Campo | Tipo | Obrigatório | Validações | Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `refreshToken` | String | Sim | `@NotBlank` | O refresh token opaco recebido no login anterior. |

**Exemplo de Payload de Envio:**
```json
{
  "refreshToken": "U7c8q1P_zT4K0xN8w2L9aBv7c3D1eF5gH2jK4lM6nO8"
}
```

#### Respostas

- **Status `200 OK`**: Sessão renovada com sucesso.
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "xK8b3N9q_wL2aV7c4D1eF5gH2jM6nO8pQ0rT2uW4yZ1",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

- **Status `401 Unauthorized`**: Token expirado, inexistente ou já revogado anteriormente.
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired refresh token",
  "path": "/auth/refresh",
  "timestamp": "2026-09-26T17:17:10.999"
}
```

- **Status `403 Forbidden`**: Se o usuário foi desativado ou bloqueado após a emissão do token.
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "User account is not active",
  "path": "/auth/refresh",
  "timestamp": "2026-09-26T17:17:15.111"
}
```

#### Exemplo cURL
```bash
curl -X POST http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "U7c8q1P_zT4K0xN8w2L9aBv7c3D1eF5gH2jK4lM6nO8"
  }'
```

---

### 2.4. `POST /auth/logout`
Encerra a sessão do usuário no cliente invalidando o refresh token fornecido. A data/hora atual é gravada na coluna `revoked_at` do registro correspondente.

#### Headers
```http
Content-Type: application/json
```

#### Corpo da Requisição (`LogoutRequestDTO`)
| Campo | Tipo | Obrigatório | Validações | Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `refreshToken` | String | Sim | `@NotBlank` | O refresh token a ser revogado. |

**Exemplo de Payload de Envio:**
```json
{
  "refreshToken": "xK8b3N9q_wL2aV7c4D1eF5gH2jM6nO8pQ0rT2uW4yZ1"
}
```

#### Respostas

- **Status `204 No Content`**: Revogação executada com sucesso (corpo vazio).
- **Status `401 Unauthorized`**: Caso o token informado não exista no banco de dados.

#### Exemplo cURL
```bash
curl -X POST http://localhost:8081/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "xK8b3N9q_wL2aV7c4D1eF5gH2jM6nO8pQ0rT2uW4yZ1"
  }'
```

---

### 2.5. `GET /auth/me`
Retorna as informações do usuário logado a partir do token JWT enviado no cabeçalho `Authorization`. O identificador do usuário é extraído da claim `sub` pelo Spring Security.

#### Headers
```http
Authorization: Bearer <seu_access_token_jwt>
```

#### Respostas

- **Status `200 OK`**: Perfil recuperado com sucesso (`MeResponseDTO`).
```json
{
  "id": "e2a3b174-8b89-42b7-8ceb-b6d8b99c011e",
  "name": "Maria Silva",
  "email": "maria.silva@example.com",
  "roles": [
    "CUSTOMER"
  ]
}
```

- **Status `401 Unauthorized`**: Token ausente, inválido, corrompido ou expirado.
- **Status `403 Forbidden`**: Usuário encontrado no token possui status diferente de `ACTIVE` (ex: `INACTIVE`, `BLOCKED`, `PENDING_VERIFICATION`).
- **Status `404 Not Found`**: Caso o UUID presente na claim `sub` do token não exista mais na base de dados.

#### Exemplo cURL
```bash
curl -X GET http://localhost:8081/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 2.6. `GET /auth/customer-role-test`
Endpoint de diagnóstico e teste de controle de acesso para o papel `CUSTOMER`. Protegido com `@PreAuthorize("hasRole('CUSTOMER')")`.

#### Headers
```http
Authorization: Bearer <seu_access_token_jwt>
```

#### Respostas

- **Status `200 OK`**: Texto plano retornado quando o usuário possui a role `CUSTOMER`.
```text
Customer authorized
```

- **Status `401 Unauthorized`**: Requisição sem token Bearer.
- **Status `403 Forbidden`**: Token válido, porém o usuário não possui o papel `CUSTOMER`.

#### Exemplo cURL
```bash
curl -X GET http://localhost:8081/auth/customer-role-test \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 2.7. `GET /auth/admin-role-test`
Endpoint de diagnóstico e teste de controle de acesso para o papel administrativo `ADMIN`. Protegido com `@PreAuthorize("hasRole('ADMIN')")`.

#### Headers
```http
Authorization: Bearer <seu_access_token_jwt>
```

#### Respostas

- **Status `200 OK`**: Texto plano retornado quando o usuário possui a role `ADMIN`.
```text
Admin authorized
```

- **Status `401 Unauthorized`**: Requisição sem token Bearer.
- **Status `403 Forbidden`**: Token válido, porém o usuário não possui o papel `ADMIN` (ex: usuário comum com role `CUSTOMER`).

#### Exemplo cURL
```bash
curl -X GET http://localhost:8081/auth/admin-role-test \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 2.8. `GET /actuator/health`
Endpoint de monitoramento de saúde do microsserviço exposto via **Spring Boot Actuator**. Utilizado para health checks de containers Docker e sondas de vivacidade (*liveness*) e prontidão (*readiness*) em orquestradores como Kubernetes. Rota pública (`permitAll`).

#### Headers
```http
Accept: application/json
```

#### Respostas

- **Status `200 OK`**: A aplicação e suas dependências essenciais estão operacionais.
```json
{
  "status": "UP"
}
```

- **Sondas Específicas para Kubernetes**:
  - `GET /actuator/health/liveness`: Verifica se o processo da JVM está vivo e funcional (`{"status":"UP"}`).
  - `GET /actuator/health/readiness`: Verifica se o microsserviço está pronto para receber tráfego HTTP de rede (`{"status":"UP"}`).

#### Exemplo cURL
```bash
curl -X GET http://localhost:8081/actuator/health
```

---

### 2.9. `GET /actuator/info`
Endpoint que provê metadados e informações da versão da aplicação. Rota pública (`permitAll`).

#### Respostas
- **Status `200 OK`**: Informações da aplicação.
```json
{}
```

#### Exemplo cURL
```bash
curl -X GET http://localhost:8081/actuator/info
```

