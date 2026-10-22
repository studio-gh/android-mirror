# Android Mirror

Protótipo experimental para espelhamento local de tela de Android em navegador no Mac, sem instalar software no Mac.

## Estado do projeto

Primeiro scaffold funcional em desenvolvimento. O Android pede autorização de captura de tela; quando ativo, abre um servidor HTTP na rede Wi-Fi local e oferece uma página web com fluxo MJPEG e envio experimental de toques pela Acessibilidade.

## Segurança e limitações

- Use somente em uma rede Wi-Fi privada e confiável.
- Esta versão inicial não implementa autenticação; qualquer dispositivo na mesma rede que conheça o endereço local pode tentar acessar a transmissão. Pare o espelhamento ao terminar.
- O controle remoto requer ativar manualmente o serviço de acessibilidade Android Mirror nas configurações do Android.
- Os controles Home/Voltar/Apps ainda não estão implementados.
- Compatibilidade, desempenho, estabilidade e comportamento em segundo plano variam entre fabricantes e versões Android.
- Não é um produto auditado. Evite expor notificações, códigos de autenticação ou outros dados sensíveis durante o teste.
- A página de espelhamento é servida pelo próprio telefone; GitHub Pages não pode capturar a tela nem controlar o Android sozinho.

## Compilar pelo GitHub Actions

O workflow `.github/workflows/build-apk.yml` tenta compilar um APK debug e publicá-lo como artifact da execução. O APK não é uma versão assinada para distribuição.

## Teste manual

1. Baixe o artifact `android-mirror-debug-apk` na execução concluída do workflow.
2. Instale o APK no Android e conecte telefone e Mac à mesma rede Wi-Fi.
3. Abra o app e toque em **Iniciar espelhamento**.
4. Autorize a captura de tela quando o Android perguntar.
5. Abra no navegador do Mac o endereço local exibido no telefone.
6. Para experimentar toques remotos, ative o serviço de acessibilidade Android Mirror nas configurações de acessibilidade.
7. Pare a transmissão no telefone ao terminar.

## Tecnologias

Kotlin, Android MediaProjection, AccessibilityService, NanoHTTPD e HTML/CSS/JavaScript sem dependências remotas no navegador.
