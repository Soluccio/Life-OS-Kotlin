# Life OS Native para Android

Aplicativo Android nativo escrito em Kotlin com Jetpack Compose. Não usa WebView, HTML, Manifest Web ou Service Worker.

## Recursos
- missões diárias com XP;
- conquistas desbloqueáveis;
- evolução semanal;
- calendário de missões concluídas;
- recompensas personalizadas;
- temporizador nativo com vibração e notificação;
- persistência offline com SharedPreferences;
- interface Material 3.

## Abrir e gerar APK
1. Instale Android Studio Ladybug ou mais recente e JDK 17.
2. Abra a pasta `LifeOS-Kotlin`.
3. Aguarde o Gradle Sync.
4. Execute em um celular Android ou emulador.
5. Para gerar APK: Build > Build App Bundles or APKs > Build APKs.
6. Para Google Play: Build > Generate Signed Bundle/APK > Android App Bundle.

O projeto usa applicationId `br.com.lifeos`, minSdk 26 e targetSdk 35.
