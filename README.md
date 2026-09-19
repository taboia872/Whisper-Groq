# Whisper-Groq

Teclado virtual (IME) + serviço de reconhecimento de voz para Android, com transcrição via **API Whisper da Groq** — rápido, sem modelo local, sem necessidade de GPU.

Baseado em [whisperIMEplus](https://github.com/woheller69/whisperIMEplus) por woheller69 (GPLv3).

## Funcionalidades

- **IME (teclado por voz)**: toca no microfone para gravar, toca de novo para parar — a transcrição é inserida direto no campo de texto
- **Modo automático (captura)**: diálogo flutuante de captura; VAD detecta silêncio e encerra sozinho, com limite configurável (5–600s) ou **sem limite** (parada manual)
- **RecognitionService**: selecionável como entrada de voz padrão do sistema
- **RecognizerIntent**: outros apps podem chamar via `ACTION_RECOGNIZE_SPEECH`
- **Tap-to-record** (sem segurar)
- **Botões de edição**: selecionar tudo, recortar, copiar, colar
- **Popups de números e pontuação**: ficam abertos para digitar sequências (só fecham ao tocar fora)
- **LED de status** no teclado (idle/ocupado/ok/erro — toque para ver o último erro)
- **Múltiplas API keys**: adicione quantas quiser (botão "+"); a transcrição alterna entre as chaves a cada requisição, nunca repetindo a última usada — e em erro de autenticação a chave inválida é pulada automaticamente
- **Temas**: modo claro, escuro, automático ou **dinâmico** (cores do papel de parede, Android 12+) × cor de destaque: roxo, azul, rosa bebê, laranja ou cinza-azulado — troca instantânea em todo o app
- **Idioma auto-detectado** pela API Whisper
- **API key criptografada** com Android Keystore (AES-256)
- Ícones padronizados em Material Icons (baseline), contraste garantido nos estados pressed (claro/escuro)

## Instalação

1. Instale o APK da aba Releases
2. Abra **Whisper-Groq** — conceda permissão de microfone (o convite para ativar o teclado fica em um banner fixo, sem competir com o diálogo de permissão)
3. Vá em **Configurações** (menu) e cole sua **API key da Groq** de https://console.groq.com — toque no ícone ⓘ para o link direto
4. Escolha o modelo Whisper (padrão: `whisper-large-v3-turbo`)
5. Ative o **Whisper-Groq** em *Configurações → Sistema → Idiomas → Teclado virtual*

## Modelos Whisper

- `whisper-large-v3-turbo` (padrão) — rápido e preciso, multilíngue
- `whisper-large-v3` — máxima qualidade, multilíngue
- `distil-whisper-large-v3-en` — só inglês, mais rápido

## Requisitos

- Android 12 ou superior (API 31+)
- Internet
- API key Groq (grátis em console.groq.com)

## Privacidade

- O áudio é gravado apenas quando você toca no microfone
- Cada gravação é enviada via HTTPS POST stateless para `api.groq.com` — sem sessão, sem cookie
- A API key fica criptografada localmente; nada sai do dispositivo além do áudio transcrito

## Build

APK assinado é gerado pelo GitHub Actions quando uma tag `v*` é empurrada.

## Licença

GPLv3 — veja LICENSE. Original: © woheller69
