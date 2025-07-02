# 필수 환경 변수만 아래와 같이 지정

```
DATABASE_URL=jdbc:postgresql://localhost:5432/sauron_db
DATABASE_USERNAME=sauron_user
DATABASE_PASSWORD=your_secure_password

GEMINI_API_KEY=your_gemini_api_key
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_DEFAULT_CHAT_ID=
TELEGRAM_ADMIN_CHAT_IDS=
```

## Required API Keys Setup

### 1. Telegram Bot Token

1. Message [@BotFather](https://t.me/BotFather) on Telegram
2. Create a new bot with `/newbot`
3. Copy the token to `TELEGRAM_BOT_TOKEN`
4. Get your chat ID from [@userinfobot](https://t.me/userinfobot)

### 2. Google Gemini API Key

1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Create a new API key
3. Copy to `GEMINI_API_KEY`

### 3. Chat ID Configuration

- Personal chat: Use your user ID from @userinfobot
- Group chat: Add bot to group, use negative group ID
- Multiple admins: Separate IDs with commas

## Security Best Practices

1. **Never commit `.env` files** to version control
2. **Use strong, unique passwords** for database connections
3. **Rotate API keys regularly**
4. **Restrict Telegram bot access** to specific chat IDs
5. **Use environment-specific configurations** for dev/staging/prod
6. **Consider secrets management systems** in production

## External Service Documentation

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Google Gemini API](https://ai.google.dev/docs)
- [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)
- [Redis Configuration](https://redis.io/docs/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
