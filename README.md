This is a Kotlin Multiplatform project targeting Web, Server.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

* [/shared](./shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```

- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Stripe Membership Prices

Membership checkout uses two fixed monthly Stripe Prices. Create the products and recurring Prices
in the same Stripe mode as `STRIPE_SECRET_KEY`, then configure the backend with:

```shell
STRIPE_PREMIUM_PRICE_ID=price_...       # $2.99/month
STRIPE_PREMIUM_PLUS_PRICE_ID=price_...  # $4.99/month
```

The existing `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, and `APP_PUBLIC_URL` variables are also
required. Configure the Stripe Billing Portal to allow customers to switch between these two Prices
if plan changes should be available through the account page.

### Monthly Tab Links

The production server creates one public tab collection link per month and stores it in Postgres.
Each collection uses the start of that month as a fixed cutoff, so later songs never appear in an
older collection. `APP_PUBLIC_URL` is used to build the shareable frontend URL.

The scheduler defaults to the `America/Phoenix` time zone. It can be changed with
`MONTHLY_TAB_LINK_TIME_ZONE`, or enabled outside production with
`MONTHLY_TAB_LINKS_ENABLED=true`.

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:

- for the Wasm target (faster, modern browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
      ```
- for the JS target (slower, supports older browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:jsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
      ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
