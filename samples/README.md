# Amostras

Exemplos de chamadas à API para uso com `curl`/Insomnia/Postman. A API usa sessão em cookie + CSRF, então mantenha um *cookie jar*.

```bash
API=http://localhost:8080/api/v1
curl -s -c cj -b cj $API/auth/csrf > /dev/null
TOKEN=$(grep XSRF-TOKEN cj | awk '{print $7}')

# login
curl -s -c cj -b cj -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
  -d @samples/login.json $API/auth/login

# cadastrar recurso
curl -s -b cj -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
  -d @samples/resource-http.json $API/resources

# dashboard
curl -s -b cj "$API/dashboard/overview?period=24h"
```

| Arquivo | Conteúdo |
|---|---|
| `login.json` | credenciais do administrador da seed |
| `register.json` | cadastro de conta + organização |
| `resource-http.json` | recurso HTTP com configuração completa de verificação |
