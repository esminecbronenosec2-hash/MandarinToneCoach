# Как установить Mandarin Tone Coach 2.0 с Galaxy Z Fold5

Предыдущий APK весил около 20 КБ, потому что не включал модели и интерфейс. В этой версии GitHub Actions скачивает **китайскую модель распознавания речи Vosk — около 42 МБ**, а потом встраивает её в APK. **Исходный ZIP маленький, это нормально: модель добавляется во время сборки.**

1. Скачай `MandarinToneCoach-v2-upload.zip` из беседы с ChatGPT на Fold5.
2. Открой свой Codespaces для репозитория `MandarinToneCoach` в Chrome. Ты уже использовал Codespaces для первой версии.
3. Через проводник Codespaces загрузи ZIP **в корневую папку репозитория** (рядом с `README.md`).
4. Вставь эти команды в терминал Codespaces целиком. Если браузер переименовал ZIP (например, добавил `-1`), поправь имя в первой строке.

```bash
unzip -o MandarinToneCoach-v2-upload.zip
git add .github/workflows/build-apk.yml app build.gradle gradle.properties settings.gradle README.md .gitignore docs LICENSE
git commit -m "Mandarin Tone Coach v2: offline Vosk and free practice"
git push origin main
```

Открой на телефоне [Actions](https://github.com/esminecbronenosec2-hash/MandarinToneCoach/actions), найди **Mandarin Tone Coach v2 APK**. После **зелёной галочки** открой запуск → **Artifacts** → **MandarinToneCoach-v2-offline-APK**, скачай архив. Внутри будет `app-debug.apk`. Установи через «Мои файлы».

Ожидаемый порядок времени: 5–15 минут, но загрузка 42 МБ зависит от серверов. Если сборка закончится красным крестиком — пришли ссылку на лог в ChatGPT, не переустанавливай GitHub и Codespaces.

**Предупреждение:** это beta. Интерфейс предварительно проверен в браузере, Java-код прошёл проверку синтаксиса, но Android APK **ещё не собран и не протестирован на твоём Fold5**. Оценка формы тонов приблизительна, особенно для слитной речи. Для полного офлайн-прослушивания поставь китайский голос TTS через настройки Android.
