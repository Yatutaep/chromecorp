@echo off
REM Нужны JDK 17 и Gradle 8.5 в PATH. Итоговый мод: build\libs\chromecorp-0.1.0.jar
gradle build && echo. && echo ГОТОВО: build\libs\chromecorp-0.1.0.jar
pause
