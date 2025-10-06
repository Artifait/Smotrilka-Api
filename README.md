
## Сборка и запуск

1. Собрать приложение:

   ```commandline
   mvn clean package
   ```

2. Запустить через Docker Compose:

   ```commandline
   docker compose up --build
   ```

3. Приложение будет доступно на [http://localhost:9090](http://localhost:9090).

---

## Примеры запросов

### Регистрация пользователя (если ещё нет):
```commandline
curl -i -X POST http://localhost:9090/register -H "Content-Type: application/json" -d "{\"login\":\"u1\",\"password\":\"p1\"}"
```


### Добавление ссылки (если нужно):
```commandline
curl -i -X POST http://localhost:9090/link -H "Content-Type: application/json" -d "{\"login\":\"u1\",\"password\":\"p1\",\"name\":\"Курс\",\"type\":\"Финансы\",\"link\":\"https://example.test\"}"
```


### Поставить лайк (+1) на ссылку id=1:
````commandline
curl -i -X POST http://localhost:9090/react -H "Content-Type: application/json" -d "{\"login\":\"u1\",\"password\":\"p1\",\"linkId\":1,\"reaction\":1}"
````


### Поставить дизлайк (-1) на ту же ссылку:
```commandline
curl -i -X POST http://localhost:9090/react -H "Content-Type: application/json" -d "{\"login\":\"u1\",\"password\":\"p1\",\"linkId\":1,\"reaction\":-1}"
```


### Отменить реакцию (0):
```commandline
curl -i -X POST http://localhost:9090/react -H "Content-Type: application/json" -d "{\"login\":\"u1\",\"password\":\"p1\",\"linkId\":1,\"reaction\":0}"
```


###Добавление ссылки в избранное:
```commandline
curl -i -X POST "http://localhost:9090/favorites/add" \-H "Content-Type: application/json" \-d "{\"login\":\"u1\",\"password\":\"p1\",\"linkId\":1}"
```

