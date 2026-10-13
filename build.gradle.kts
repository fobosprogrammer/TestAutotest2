import java.time.Duration

plugins {
    id("java")
    id("application")
    id("io.qameta.allure") version "4.3.0"
}

group = "ru.t1.authomatization"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.codeborne:selenide:7.17.0")
    implementation("org.aeonbits.owner:owner:1.0.12")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.rest-assured:rest-assured:5.5.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    testImplementation("io.qameta.allure:allure-rest-assured:2.35.5")
    testImplementation("io.qameta.allure:allure-selenide:2.35.5")
    testImplementation("io.qameta.allure:allure-java-commons:2.35.5")
}

allure {
    // Версия report-рантайма — чем будут собираться HTML-отчёты:
    //   2.x  — классический Allure 2: скачивает allure-commandline (zip);
    //   3.x  — новая Allure 3: дополнительно провижинит Node.js.
    // Для стабильности вебинара берём проверенный годами Allure 2.
    version.set("2.46.1")

    adapter {
        // Версия Java-адаптеров Allure (allure-jupiter, allure-rest-assured,
        // allure-selenide, allure-java-commons). ДОЛЖНА совпадать с версиями
        // зависимостей в блоке dependencies выше (у нас — 2.35.5).
        allureJavaVersion.set("2.35.5")

        frameworks {
            // JUnit Jupiter (поддерживает и JUnit 5, и JUnit 6):
            // включаем авторегистрацию слушателя AllureJunit5 — результаты
            // каждого теста автоматически пишутся в build/allure-results.
            jupiter {
                autoconfigureListeners.set(true)
            }
        }
    }
}

tasks.named<io.qameta.allure.gradle.report.tasks.AllureReport>("allureReport") {
    clean.set(true)
}

tasks.test {
    useJUnitPlatform {
        // Фильтрация по тегу прямо из командной строки:
        //   ./gradlew test -Ptags=integration
        //   ./gradlew test -Ptags="smoke | slow"
        // Если тег указан — запускаем только его (в т.ч. выражение),
        // иначе — обычные юнит-тесты без тяжёлых групп.
//        val tags = providers.gradleProperty("tags").orNull
//        if (tags != null) {
//            includeTags(tags)
//        } else {
//            // Юнит-тесты должны быть быстрыми, поэтому остальные группы «отдаём»
//            // их собственным задачам. Исключаем тяжёлые, smoke, ui, api и integration
//            // по их JUnit-тегам прямо на уровне JUnit Platform.
//            excludeTags("slow", "smoke", "ui", "api", "integration")
//        }
    }
    systemProperties(
        mapOf(
            "api.username" to project.findProperty("api.username"),
            "api.password" to project.findProperty("api.password"),
            "api.base.uri" to project.findProperty("api.base.uri"),
            "api.port" to project.findProperty("api.port"),
            "allure.results.directory" to "${layout.buildDirectory.get().asFile}/allure-results"
        ))
}

tasks.named<io.qameta.allure.gradle.report.tasks.AllureReport>("allureReport") {
    clean.set(true)
}

// ---------- 5а. REST API-тесты (RestAssured) ----------
// Тесты на ручки HTTP-сервиса (см. пакет ru.t1.authomatization.api) отбираются по тегу @Tag("api").
// Они требуют ЗАПУЩЕННЫЙ сервер (по умолчанию http://127.0.0.1:8080, см. класс Endpoints).
val apiTest by tasks.register<Test>("apiTest") {
    group = "verification"
    description = "Runs REST API tests with RestAssured (JUnit tag 'api'). Needs a running server!"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform {
        includeTags("api")
    }

    // Пробрасываем конфигурацию в тестовую JVM как системные свойства.
    // Источник значений (по приоритету):
    //   1) -Dapi.xxx=...  — системное свойство из командной строки (./gradlew apiTest -Dapi.username=...)
    //   2) -PapiXxx=...   — gradle-свойство из командной строки (./gradlew apiTest -PapiUsername=...)
    //   3) значение по умолчанию
    // Тонкость: Gradle НЕ передаёт системные свойства из своей JVM в тестовую
    // автоматически — поэтому явно читаем их через providers.systemProperty
    // и кладём в тестовую JVM через systemProperty(...).
    systemProperty("api.base.uri", providers.systemProperty("api.base.uri")
        .orElse(providers.gradleProperty("apiBaseUri"))
        .orElse("http://127.0.0.1")
        .get())
    systemProperty("api.port", providers.systemProperty("api.port")
        .orElse(providers.gradleProperty("apiPort"))
        .orElse("8080")
        .get())
    systemProperty("api.username", providers.systemProperty("api.username")
        .orElse(providers.gradleProperty("apiUsername"))
        .orElse("admin")
        .get())
    systemProperty("api.password", providers.systemProperty("api.password")
        .orElse(providers.gradleProperty("apiPassword"))
        .get())

    // Показываем вывод тестовой JVM в консоли (stdout/stderr).
    // Без этого Gradle «глотает» логи RestAssured из .log().all() —
    // они уходят в build/reports, но не видны в терминале.
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val smokeTest by tasks.register<Test>("smokeTest") {
    group = "verification"
    description = "Runs quick smoke tests (JUnit tag 'smoke')"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform {
        includeTags("smoke")
    }

    systemProperty("api.base.uri", providers.systemProperty("api.base.uri")
        .orElse(providers.gradleProperty("apiBaseUri"))
        .orElse("http://127.0.0.1")
        .get())
    systemProperty("api.port", providers.systemProperty("api.port")
        .orElse(providers.gradleProperty("apiPort"))
        .orElse("8080")
        .get())
    systemProperty("api.username", providers.systemProperty("api.username")
        .orElse(providers.gradleProperty("apiUsername"))
        .orElse("admin")
        .get())
    systemProperty("api.password", providers.systemProperty("api.password")
        .orElse(providers.gradleProperty("apiPassword"))
        .get())
    // Упал первый же тест — дальше гонять бессмысленно, останавливаемся
    failFast = true

    // Smoke-тесты обязаны быть молниеносными
    timeout = Duration.ofSeconds(30)
}