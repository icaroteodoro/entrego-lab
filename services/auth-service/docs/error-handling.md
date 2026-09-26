# 🚨 Tratamento de Erros e Exceções - Auth Service

Este documento descreve a estratégia de interceptação global de erros, o formato padronizado de resposta para falhas HTTP (`ApiError`), o mapeamento de exceções e as boas práticas de segurança defensiva implementadas no **Auth Service**.

---

## 1. Estratégia Centralizada de Erros

O tratamento de exceções é centralizado através da classe `GlobalExceptionHandler`, anotada com `@RestControllerAdvice`. Esse padrão desacopla as regras de negócio dos controladores REST e garante que qualquer erro durante o processamento de uma requisição resulte em uma resposta JSON previsível, consistente e segura para o cliente.

### 1.1. Contrato da Resposta de Erro (`ApiError`)
Toda falha interceptada é serializada utilizando o record imutável `ApiError`:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/auth/login",
  "timestamp": "2026-09-26T17:20:00.123456"
}
```

| Campo | Tipo | Descrição |
| :--- | :--- | :--- |
| `status` | `int` | Código numérico de status HTTP (ex: 400, 401, 403). |
| `error` | `String` | Frase descritiva padrão do status HTTP (ex: `"Bad Request"`, `"Unauthorized"`, `"Forbidden"`). |
| `message` | `String` | Mensagem descritiva e higienizada informando a causa do problema. |
| `path` | `String` | URI exata do endpoint em que ocorreu o erro (obtida via `HttpServletRequest.getRequestURI()`). |
| `timestamp` | `LocalDateTime` | Data e hora em que a resposta de erro foi montada. |

---

## 2. Matriz de Mapeamento de Exceções

Abaixo estão todas as exceções tratadas nativamente pelo `GlobalExceptionHandler`:

| Exceção Capturada | Status HTTP | Código HTTP | Mensagem Retornada | Cenário Comum |
| :--- | :--- | :--- | :--- | :--- |
| `MethodArgumentNotValidException` | `BAD_REQUEST` | **400** | `"<campo>: <mensagem_de_validacao>"` | Violação de anotações `@Valid` (`@NotBlank`, `@Email`, `@Size`). |
| `BadCredentialsException` | `UNAUTHORIZED` | **401** | `"Invalid email or password"` | E-mail inexistente no banco ou senha informada incorreta no login. |
| `InvalidRefreshTokenException` | `UNAUTHORIZED` | **401** | `"Invalid refresh token"` ou `"Invalid or expired refresh token"` | Refresh token inexistente, corrompido, expirado ou previamente revogado. |
| `DisabledException` | `FORBIDDEN` | **403** | `"User account is not active"` | Tentativa de login ou renovação de token para usuário com status diferente de `ACTIVE`. |

---

## 3. Catálogo de Cenários e Exemplos de Resposta

### 3.1. Erro de Validação de Campos (HTTP 400 Bad Request)
- **Disparado por**: `MethodArgumentNotValidException`
- **Lógica no Handler**: Captura o primeiro erro de campo da lista (`getFieldErrors().stream().findFirst()`) e formata como `campo: erro`.

**Cenário 1: E-mail em formato inválido no registro**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "email: must be a well-formed email address",
  "path": "/auth/register",
  "timestamp": "2026-09-26T17:21:10.512"
}
```

**Cenário 2: Senha com menos de 8 caracteres**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "password: size must be between 8 and 100",
  "path": "/auth/register",
  "timestamp": "2026-09-26T17:21:15.823"
}
```

**Cenário 3: Campo obrigatório em branco no login**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "email: must not be blank",
  "path": "/auth/login",
  "timestamp": "2026-09-26T17:21:20.104"
}
```

---

### 3.2. Credenciais Inválidas (HTTP 401 Unauthorized)
- **Disparado por**: `BadCredentialsException`
- **Diretriz de Segurança**: Por recomendação estrita do OWASP, **a mensagem é unificada**:
  - Se o e-mail não existir -> lança `BadCredentialsException("Invalid email or password")`
  - Se a senha estiver errada -> lança `BadCredentialsException("Invalid email or password")`
- **Objetivo**: Evitar **Enumeração de Usuários** (*User Enumeration*). Um invasor não consegue deduzir se um e-mail existe ou não no sistema através das mensagens de erro do login.

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/auth/login",
  "timestamp": "2026-09-26T17:22:00.015"
}
```

---

### 3.3. Refresh Token Inválido ou Expirado (HTTP 401 Unauthorized)
- **Disparado por**: `InvalidRefreshTokenException`
- **Cenários**:
  - Hash do token não localizado na tabela `refresh_tokens`.
  - Token com data atual posterior à coluna `expires_at` (`isExpired() == true`).
  - Token com coluna `revoked_at` preenchida (`isRevoked() == true`), indicando logout anterior ou substituição por rotação (RTR).

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired refresh token",
  "path": "/auth/refresh",
  "timestamp": "2026-09-26T17:23:45.321"
}
```

---

### 3.4. Usuário Inativo ou Bloqueado (HTTP 403 Forbidden)
- **Disparado por**: `DisabledException`
- **Cenário**: O usuário informou credenciais corretas ou um refresh token matematicamente válido, porém o campo `status` da entidade `User` é diferente de `UserStatus.ACTIVE` (ex: `INACTIVE`, `BLOCKED`, `PENDING_VERIFICATION`).

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "User account is not active",
  "path": "/auth/login",
  "timestamp": "2026-09-26T17:24:12.789"
}
```

---

## 4. Tratamento de Exceções Não Mapeadas e Evoluções Recomendadas

### 4.1. Duplicidade de E-mail no Cadastro (`UserService.create`)
Atualmente, no método `create` do `UserService`:
```java
if (userRepository.existsUserByEmail(request.email())) {
    throw new IllegalAccessException("Email already registered");
}
```
Como `IllegalAccessException` é uma exceção checada padrão do Java e ainda não possui um `@ExceptionHandler` dedicado no `GlobalExceptionHandler`, ela gera um erro genérico HTTP 500 do servidor.

#### 💡 Recomendação de Evolução
Criar uma exceção de domínio personalizada (ex: `EmailAlreadyExistsException`) e tratá-la retornando **HTTP 409 Conflict**:
```java
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}

// No GlobalExceptionHandler:
@ExceptionHandler(EmailAlreadyExistsException.class)
public ResponseEntity<ApiError> handleEmailConflict(
        EmailAlreadyExistsException ex, HttpServletRequest req) {
    return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
}
```

### 4.2. Usuário Não Encontrado na Introspecção (`/auth/me`)
Em `AuthService.me(UUID userId)`:
```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
```
Recomenda-se mapear `IllegalArgumentException` ou criar `UserNotFoundException` mapeada para **HTTP 404 Not Found**.

### 4.3. RFC 7807 (ProblemDetail)
O Spring Boot 3+ oferece suporte nativo ao padrão `ProblemDetail` (RFC 7807). Caso o projeto decida padronizar todos os microsserviços do **Entrego** sob essa RFC, a transição do record `ApiError` para `ProblemDetail` pode ser feita diretamente no `GlobalExceptionHandler` sem impacto na lógica dos serviços de domínio.
