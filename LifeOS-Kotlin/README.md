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

## Compilação automática pelo GitHub Actions

O fluxo `.github/workflows/build-apk.yml` compila um APK de teste automaticamente a cada envio para a branch `main` e também pode ser executado manualmente.

1. Abra a aba **Actions** do repositório.
2. Selecione **Gerar APK Life OS**.
3. Clique em **Run workflow**.
4. Aguarde a execução terminar.
5. Abra a execução concluída e baixe o artefato **Life-OS-debug-apk**.

O arquivo baixado contém `app-debug.apk`. O artefato fica disponível por 30 dias.
