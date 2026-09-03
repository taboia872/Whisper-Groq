# Whisper-Groq

Teclado virtual (IME) + serviço de reconhecimento de voz para Android, com transcrição via **API Whisper da Groq** — rápido, sem modelo local, sem necessidade de GPU.

Baseado em [whisperIMEplus](https://github.com/woheller69/whisperIMEplus) por woheller69 (GPLv3).

## Funcionalidades

- **IME (teclado por voz)**: toca no microfone para gravar, toca de novo para parar — a transcrição é inserida direto no campo de texto
- **RecognitionService**: selecionável como entrada de voz padrão do Android
- **RecognizerIntent**: outros apps podem chamar via `ACTION_RECOGNIZE_SPEECH`
- **Visual glass**: painéis semi-transparentes, cantos arredondados, cores Groq
- **Tap-to-record** (sem segurar o dedo), até 30s, VAD com detecção de silêncio no modo auto
- **Botões de pontuação**: `. , ? !` + Enter + Backspace
- **Idioma auto-detectado** pela API Whisper
- **Nenhum download de modelo** — o áudio é enviado pra Groq, só precisa de uma API key

## Instalação

1. Instale o APK da aba Releases
2. Abra **Whisper-Groq** (ícone no launcher) — ele vai pedir permissão de microfone
3. Vá em **Configurações** (menu) e cole sua **API key da Groq** de https://console.groq.com
4. Escolha o modelo Whisper (padrão: `whisper-large-v3-turbo` — melhor custo/latência)
5. Ative o **Whisper-Groq** em *Configurações → Sistema → Idiomas → Teclado virtual* no Android

## Modelos Whisper disponíveis

| Modelo | Idioma | Velocidade | Precisão |
|---|---|---|---|
| `whisper-large-v3-turbo` (padrão) | todos | ~8x realtime | ótima |
| `whisper-large-v3` | todos | ~6x realtime | máxima |
| `distil-whisper-large-v3-en` | só inglês | mais rápido | boa |

## Requisitos

- Conexão com internet
- Chave da API Groq (plano gratuito tem limites generosos)

## Privacidade

- O áudio é gravado apenas quando você toca no microfone (máx 30s por sessão)
- Enviado via HTTPS para `api.groq.com` — nenhum dado fica no app nem é compartilhado com terceiros além da Groq
- A API key é armazenada localmente nas SharedPreferences do app

## Build

APK assinado é gerado pelo GitHub Actions quando uma tag `v*` é empurrada. Para build local:

```bash
./gradlew assembleDebug                    # APK debug (sem assinar)
export KEYSTORE_FILE=/path/to/keystore.jks # release assinado
./gradlew assembleRelease
```

## Licença

GPLv3 — veja LICENSE. Original: © woheller69
