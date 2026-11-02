# Projeto: app Android simples de cobrança de condomínio

Sou iniciante. Você escreve o código; eu só dou os prompts de cada etapa.

## REGRAS DE TRABALHO
- Faça SOMENTE a etapa que eu pedir. Não adicione funcionalidades, telas, bibliotecas ou melhorias que eu não pedi.
- Código simples, com comentários curtos em português. Textos do app em português do Brasil.
- Não há Android Studio, JDK nem Android SDK neste computador. NÃO tente compilar localmente.
- A compilação é feita só no GitHub Actions. Ao terminar cada etapa: faça commit, push, acompanhe o workflow com o GitHub CLI (gh) e, se falhar, leia o log, corrija e envie de novo, até passar.
- Se não conseguir usar o gh, me diga para colar o log do erro aqui.
- Ao final: liste os arquivos criados/alterados (1 linha cada) e diga como testar no celular.

## REPOSITÓRIO PÚBLICO (segurança)
- O repositório é público. NUNCA coloque no repositório: keystores, senhas, chaves, tokens, nem dados reais (nomes de moradores, chave PIX, nome de condomínio, valores reais).
- Em testes, exemplos e textos de teste use somente dados fictícios (ex.: "Morador Teste", chave PIX "teste@exemplo.com").
- Nunca imprima segredos em logs do workflow.
- Adicione ao .gitignore: *.keystore, *.jks, local.properties, keystore.b64.

## O APP
App Android offline para uma síndica, usado em um único aparelho. Tudo fica salvo no aparelho. Ela cadastra condomínio, unidades, moradores e despesas; define a regra de cobrança de cada despesa; fecha a cobrança do mês; o app gera uma imagem por unidade para ela compartilhar pelo WhatsApp (Sharesheet do Android); mantém histórico resumido; exporta o histórico; e permite excluir o histórico.

## STACK
Kotlin, Jetpack Compose (Material 3), Room (com KSP), Navigation Compose, ViewModel + StateFlow. Sem Hilt, sem internet, minSdk 26. Imagens com Canvas/Bitmap. Compartilhar com FileProvider + Intent.ACTION_SEND. Salvar arquivos com ActivityResultContracts.CreateDocument.
Use versões estáveis e compatíveis entre si de Gradle, Android Gradle Plugin, Kotlin, KSP e Compose, e me diga quais escolheu.

## COMPILAÇÃO
Workflow em .github/workflows/build.yml: dispara em push; ubuntu-latest, JDK 17 (actions/setup-java), Gradle com gradle/actions/setup-gradle em versão fixa, executando "gradle testDebugUnitTest assembleDebug --stacktrace". Publica o APK debug como artefato com retention-days: 7; publica também os relatórios de teste (if: always()). Pode usar "gradle" direto, sem wrapper.
Assinatura: se existirem os secrets KEYSTORE_BASE64 e KEYSTORE_PASSWORD, o workflow decodifica o keystore para um arquivo temporário e o buildType debug assina com ele (alias: debug, senha vinda de variável de ambiente). Se os secrets não existirem, usa a assinatura debug padrão, para o workflow não falhar.

## REGRAS DE CÁLCULO
- Dinheiro sempre em centavos (Long). Nunca Double/Float.
- Cada despesa tem uma regra: (1) valor fixo por unidade; (2) valores diferentes por grupo ou unidade; (3) rateio igualitário; (4) rateio proporcional. Em qualquer regra, cada unidade pode ser marcada como isenta.
- Unidade isenta paga 0 e a despesa não aparece na imagem dela. Os rateios dividem só entre as não isentas.
- Rateio igualitário: valor total ÷ nº de unidades não isentas. A sobra de centavos vai de 1 em 1 para as primeiras unidades, na ordem de cadastro. A soma deve fechar exatamente no total.
- Rateio proporcional: proporcional ao "peso" de cada unidade (campo decimal, padrão 1), com a mesma regra de sobra de centavos.
- "Grupo" = várias unidades recebendo o mesmo valor. Não é um cadastro separado.
- Cada unidade tem um nome de morador.
- Cobrança fechada guarda cópia dos valores, nomes e dados do dia. Mudar cadastros depois não altera o histórico.
- Imagens de meses fechados podem ser vistas e compartilhadas de novo, geradas a partir dos dados congelados.

## FORA DO ESCOPO (não fazer)
Login, nuvem, sincronização, PDF, QR Code, controle de pago/pendente, notificações, gráficos, vários condomínios, backup, restaurar backup, envio direto pelo WhatsApp, temas extras.
