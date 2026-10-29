# PRD — DevStreak
## Plataforma social de constância para desenvolvedores

**Versão:** 1.0  
**Status:** Proposta para desenvolvimento do MVP  
**Tipo de produto:** SaaS web  
**Público-alvo:** Desenvolvedores que desejam manter consistência nos estudos e no desenvolvimento de software  
**Integração principal:** GitHub  
**Objetivo do MVP:** Validar se desafios competitivos e acompanhamento automático de atividade ajudam desenvolvedores a manter uma rotina consistente.

---

# 1. Visão geral do produto

## 1.1. Resumo executivo

O DevStreak é uma plataforma de desafios de constância para desenvolvedores, inspirada em aplicativos sociais de acompanhamento de hábitos e competições entre amigos.

Os usuários poderão conectar suas contas do GitHub, criar grupos privados, convidar amigos e participar de desafios com metas individuais de dias ativos.

A plataforma verificará automaticamente os commits realizados nos repositórios elegíveis. Cada dia em que o participante realizar pelo menos um commit válido contará como um dia cumprido, independentemente da quantidade de commits naquele dia.

Cada desafio terá uma meta definida pelo criador. Os participantes competirão individualmente, e o primeiro a alcançar a meta será declarado vencedor.

O produto terá dois modos de verificação:

1. **Repositórios selecionados:** o grupo utiliza até três repositórios específicos como base de validação.
2. **Repositórios próprios:** cada participante poderá contabilizar commits somente em repositórios elegíveis de sua própria conta GitHub, sem precisar selecionar repositórios específicos para o grupo.

O produto será desenvolvido inicialmente como um monólito modular, com arquitetura preparada para evoluir para um SaaS comercial.

## 1.2. Problema

Desenvolvedores frequentemente têm dificuldade para manter consistência nos estudos, na prática de programação e na evolução de projetos pessoais.

Entre os problemas identificados estão:

- Falta de compromisso externo com os objetivos individuais.
- Dificuldade para manter uma rotina de estudos por períodos prolongados.
- Ausência de mecanismos sociais específicos para incentivar a constância na programação.
- Falta de visibilidade sobre a evolução dos amigos.
- Dificuldade de acompanhar o progresso de grupos sem depender de registros manuais.
- Possibilidade de perder a motivação quando não existe uma meta compartilhada.

## 1.3. Proposta de valor

**Transformar a constância no desenvolvimento de software em um desafio social verificável.**

A plataforma elimina a necessidade de registrar manualmente cada dia de atividade, utiliza o GitHub como fonte de verificação e introduz metas, rankings, sequências e competição entre amigos.

## 1.4. Princípios do produto

- Automação da validação.
- Baixa fricção para participar de um desafio.
- Regras transparentes e iguais para todos os participantes de um grupo.
- Privacidade dos repositórios e do código-fonte.
- Competição baseada em dias válidos, não no volume de commits.
- Simplicidade na criação e administração dos desafios.
- Infraestrutura enxuta, com custos controlados.

---

# 2. Objetivos e métricas

## 2.1. Objetivos do MVP

1. Permitir que um usuário autentique sua conta GitHub.
2. Permitir a criação e participação em grupos privados.
3. Implementar os dois modos de verificação de commits.
4. Validar automaticamente dias de atividade.
5. Calcular o progresso individual de cada participante.
6. Identificar o primeiro participante que alcançar a meta.
7. Apresentar calendários, sequências e rankings.
8. Garantir que eventos duplicados não produzam progresso duplicado.
9. Estabelecer uma base técnica adequada para futura monetização.

## 2.2. Indicadores de sucesso

As metas abaixo são hipóteses iniciais para o piloto e devem ser avaliadas após o lançamento.

| Indicador | Meta inicial |
|---|---:|
| Usuários que conectam o GitHub após se cadastrar | ≥ 70% |
| Grupos que recebem pelo menos dois participantes | ≥ 60% |
| Participantes que validam o primeiro dia | ≥ 60% |
| Eventos processados sem duplicidade de progresso | 100% |
| Eventos elegíveis processados em até 2 minutos | ≥ 95% |
| Participantes ativos após quatro semanas | Medir e estabelecer baseline |
| Desafios que chegam à meta ou ao encerramento | Medir no piloto |

A latência será medida a partir do recebimento do evento pelo backend. Eventos atrasados pelo próprio GitHub serão monitorados separadamente.

## 2.3. Fora do objetivo inicial

O MVP não pretende:

- Provar que o usuário estudou durante determinado número de horas.
- Avaliar automaticamente a qualidade do código.
- Competir com plataformas de cursos.
- Substituir o GitHub.
- Oferecer uma rede social completa.
- Gerenciar equipes empresariais de desenvolvimento.
- Garantir que todos os commits representem trabalho intelectual original.

O produto comprova atividade elegível segundo regras definidas, não o aprendizado efetivo.

---

# 3. Público-alvo e personas

## 3.1. Persona A — Estudante de programação

Quer aprender uma linguagem, praticar algoritmos e construir projetos de portfólio.

**Necessidades:**
- Manter uma rotina.
- Visualizar o progresso.
- Compartilhar metas com amigos.
- Receber incentivo quando estiver perdendo a sequência.

## 3.2. Persona B — Desenvolvedor em início de carreira

Deseja evoluir tecnicamente fora do trabalho e manter projetos pessoais ativos.

**Necessidades:**
- Separar atividade profissional de projetos próprios.
- Definir metas alcançáveis.
- Acompanhar o progresso ao longo de semanas e meses.

## 3.3. Persona C — Desenvolvedor que deseja estudar em grupo

Já possui amigos que estudam ou desenvolvem projetos em conjunto.

**Necessidades:**
- Criar desafios.
- Convidar participantes.
- Comparar resultados.
- Estabelecer metas coletivas ou individuais.
- Competir de forma transparente.

## 3.4. Persona D — Criador do desafio

É responsável por configurar e administrar um grupo.

**Necessidades:**
- Definir a meta.
- Escolher o modo de verificação.
- Configurar os repositórios quando aplicável.
- Gerenciar convites e participantes.
- Consultar o andamento do desafio.

---

# 4. Escopo funcional do MVP

O MVP será dividido nos seguintes módulos:

1. Autenticação e perfil.
2. Integração com GitHub.
3. Grupos e convites.
4. Configuração de desafios.
5. Verificação de commits.
6. Progresso individual e streaks.
7. Ranking e definição do vencedor.
8. Dashboard e calendário.
9. Configurações, privacidade e desconexão.
10. Administração técnica e monitoramento.

---

# 5. Autenticação e perfil

## 5.1. Cadastro e login

O usuário deverá conseguir:

- Criar uma conta na plataforma.
- Entrar e sair da conta.
- Recuperar o acesso, quando aplicável ao método de autenticação.
- Conectar sua conta GitHub.
- Consultar o estado da conexão com o GitHub.
- Desconectar a integração.

O MVP poderá utilizar autenticação por e-mail ou por um provedor externo. A conexão GitHub deverá ser tratada como uma autorização independente, mesmo quando houver autenticação social.

## 5.2. Dados do perfil

Dados mínimos:

- Identificador interno.
- Nome de exibição.
- Nome de usuário da plataforma.
- Avatar.
- Identificador imutável da conta GitHub.
- Nome de usuário GitHub.
- Data de cadastro.
- Estado da integração.
- Fuso horário preferido.
- Preferências de notificações.

O identificador numérico da conta GitHub será utilizado como referência principal da identidade externa. O nome de usuário poderá mudar.

## 5.3. Critérios de aceite

- O usuário consegue criar uma conta e acessar a plataforma.
- A conexão GitHub é armazenada de forma segura.
- A desconexão impede novas consultas que dependam da autorização revogada.
- O usuário consegue consultar quais permissões foram concedidas.
- Nenhum token de acesso é disponibilizado ao frontend de forma desnecessária.

---

# 6. Integração com o GitHub

## 6.1. Tecnologia de integração

A integração será implementada utilizando uma GitHub App, sempre que compatível com os requisitos de autorização e acesso aos repositórios.

A aplicação deverá utilizar OAuth ou o fluxo de autorização correspondente à configuração adotada para vincular a conta do usuário.

A GitHub App será responsável por permitir o acesso autorizado aos repositórios e receber eventos de atividade.

A implementação deverá separar:

- Identidade do usuário.
- Autorização da conta.
- Acesso aos repositórios.
- Recebimento de eventos.
- Validação de commits.
- Revogação de acesso.

## 6.2. Permissões mínimas

A aplicação solicitará somente as permissões necessárias para:

- Identificar a conta autorizada.
- Listar ou consultar os repositórios necessários.
- Consultar metadados e commits elegíveis.
- Receber eventos de push.

O acesso a conteúdo privado dependerá das permissões concedidas pelo usuário e das regras de instalação da GitHub App.

O MVP não deverá solicitar permissão para escrever código, criar commits ou modificar repositórios.

## 6.3. Segurança dos eventos

O backend deverá:

1. Receber o webhook.
2. Validar sua assinatura criptográfica.
3. Verificar se o evento possui formato esperado.
4. Identificar a instalação e o repositório relacionados.
5. Persistir o evento recebido.
6. Garantir idempotência.
7. Processar a atividade elegível.
8. Atualizar o progresso do participante.

Eventos repetidos deverão ser reconhecidos por identificadores estáveis ou por uma chave de idempotência adequada ao tipo de evento.

## 6.4. Recuperação de eventos perdidos

O sistema não deverá depender exclusivamente da entrega de webhooks.

Será implementada uma rotina periódica de reconciliação para verificar eventos ou commits recentes que possam ter sido perdidos durante indisponibilidades.

A rotina deverá:

- Operar em uma janela temporal limitada.
- Respeitar os limites da API do GitHub.
- Evitar reprocessamento desnecessário.
- Ser idempotente.
- Registrar falhas de sincronização.
- Permitir tentativas posteriores.

A frequência da reconciliação será definida de acordo com os limites de uso e os resultados do piloto.

## 6.5. Limitações da integração

A interface deverá informar quando:

- A conexão estiver expirada ou revogada.
- Um repositório não estiver acessível.
- Uma permissão necessária não tiver sido concedida.
- A verificação estiver temporariamente indisponível.

O sistema não deverá declarar automaticamente que o participante descumpriu a meta quando a integração estiver indisponível.

---

# 7. Grupos e desafios

## 7.1. Conceito

Um grupo será um espaço privado no qual participantes competem em um desafio de constância.

No MVP, cada grupo terá um desafio ativo por vez. Essa restrição reduz a complexidade de estados, rankings e regras de encerramento.

## 7.2. Dados do grupo

- Nome.
- Descrição opcional.
- Criador.
- Código ou link de convite.
- Modo de verificação.
- Meta individual.
- Data e hora de início.
- Fuso horário de referência.
- Estado do desafio.
- Limite de participantes.
- Data de criação.

## 7.3. Criação do grupo

O criador deverá:

1. Informar nome e descrição opcional.
2. Escolher o modo de verificação.
3. Definir a meta individual.
4. Configurar o início do desafio.
5. Selecionar os repositórios, quando necessário.
6. Revisar as regras.
7. Criar o grupo.
8. Compartilhar o convite.

## 7.4. Convites

O criador poderá convidar amigos por link.

O sistema deverá permitir:

- Gerar um convite exclusivo para o grupo.
- Revogar um convite.
- Impedir a entrada de usuários não autenticados.
- Evitar a duplicação de membros.
- Exibir a lista de participantes.

Os convites poderão ser utilizados até serem revogados ou até o encerramento do período de admissão definido pelo criador.

## 7.5. Papéis

**Criador:**
- Configura o desafio antes de iniciá-lo.
- Gerencia convites.
- Consulta participantes.
- Pode cancelar o desafio, conforme as regras definidas.

**Participante:**
- Visualiza o grupo.
- Consulta o próprio progresso e o dos demais.
- Consulta o ranking.
- Pode sair do grupo conforme as regras de participação.

A transferência de propriedade do grupo poderá ser postergada para uma versão futura.

---

# 8. Modos de verificação

## 8.1. Modo A — Repositórios selecionados

O criador seleciona até três repositórios que serão a base de verificação do grupo.

### Regras

- O grupo poderá utilizar um, dois ou três repositórios.
- Nenhum commit em repositório fora da lista será elegível.
- Todos os participantes estarão sujeitos à mesma lista de repositórios.
- O acesso necessário deverá ser autorizado.
- A aplicação verificará a identidade do autor do commit.
- O commit deverá ocorrer após o início do desafio.
- Um mesmo dia poderá ser contabilizado apenas uma vez por participante.

### Consideração de produto

Os repositórios podem pertencer a usuários ou organizações diferentes, desde que a aplicação tenha autorização suficiente para verificar a atividade.

Todos os participantes deverão conseguir contribuir nos repositórios selecionados para que esse modo seja adequado ao grupo. A aplicação não deverá exigir acesso de escrita para realizar a verificação, embora a participação efetiva dependa das permissões de contribuição de cada repositório.

## 8.2. Modo B — Repositórios próprios

Cada participante utilizará seus próprios repositórios elegíveis, sem que o criador precise selecionar repositórios para o grupo.

### Regra principal

Um commit será elegível somente quando o repositório pertencer à conta pessoal GitHub autenticada do participante.

O nome do autor ou o e-mail presente no commit não será suficiente para comprovar a propriedade do repositório.

### Exemplos

| Repositório | Resultado |
|---|---|
| Repositório público pertencente ao participante | Elegível |
| Repositório privado pertencente ao participante, com autorização adequada | Elegível |
| Repositório de propriedade da empresa em que o participante trabalha | Não elegível |
| Repositório pertencente a um amigo | Não elegível |
| Repositório pertencente a uma organização | Não elegível por padrão |
| Fork pertencente ao participante | Elegibilidade conforme as regras de propriedade e autoria implementadas |

### Objetivo

Permitir que cada membro participe utilizando seus próprios projetos, sem precisar compartilhar o mesmo repositório com o restante do grupo.

### Observação sobre autoria

A propriedade do repositório e a autoria do commit são conceitos distintos. Para evitar que qualquer atividade de terceiros conte como atividade do participante, o sistema deverá verificar também a identidade do autor ou do committer conforme a regra de elegibilidade implementada.

Commits realizados por ferramentas automatizadas não deverão contar automaticamente sem uma política explícita de elegibilidade.

## 8.3. Seleção do modo

O modo será definido na criação do grupo.

No MVP, o modo não poderá ser alterado depois que o desafio começar. Se o criador precisar alterar a modalidade, deverá cancelar o desafio e criar outro, preservando o histórico do desafio anterior.

---

# 9. Regras de negócio dos commits

## 9.1. Definição de dia válido

Um dia será considerado válido quando houver pelo menos um commit elegível associado ao participante em um dos repositórios permitidos pelo grupo.

A validação será automática.

## 9.2. Contagem diária

- Um commit elegível equivale a um dia válido.
- Vários commits no mesmo dia continuam equivalendo a um único dia válido.
- Commits em vários repositórios elegíveis no mesmo dia continuam equivalendo a um único dia válido.
- Commits duplicados não poderão gerar dias adicionais.
- Commits fora da janela temporal do desafio não serão contabilizados.
- Commits em repositórios não elegíveis não serão contabilizados.
- Commits anteriores ao início do desafio não serão contabilizados.

## 9.3. Data e fuso horário

O sistema deverá armazenar timestamps em UTC e converter a atividade para o fuso horário de referência do desafio.

O fuso horário será definido na criação do grupo e permanecerá fixo durante o desafio.

Essa regra garante que todos os participantes de um grupo tenham a mesma definição de dia válido.

## 9.4. Critério de elegibilidade do commit

A validação deverá considerar:

- Identidade da conta GitHub.
- Repositório associado ao evento.
- Propriedade do repositório, conforme o modo.
- Data e hora do commit.
- Associação do commit à atividade recebida.
- Estado do desafio.
- Duplicidade do evento.
- Política de commits automatizados.
- Exclusão de commits anteriores ao início do desafio.

O sistema não deverá confiar exclusivamente em nomes, e-mails ou mensagens de commit.

## 9.5. Histórico e correções

Cada dia validado deverá possuir referência aos eventos ou commits que fundamentaram sua validação.

Se uma sincronização posterior identificar uma inconsistência, o sistema deverá recalcular o progresso sem duplicar dias.

Alterações no GitHub, como remoção ou reescrita de histórico, deverão ser tratadas de acordo com uma política de correção documentada. O MVP deverá preservar um registro de auditoria das correções que afetem o resultado do desafio.

## 9.6. Política para indisponibilidade

Se o GitHub estiver indisponível ou a conexão estiver temporariamente inválida:

- O sistema exibirá o estado da integração.
- O processamento será tentado novamente quando possível.
- O participante não será penalizado automaticamente por uma falha interna.
- O progresso será atualizado após a reconciliação dos eventos elegíveis.

---

# 10. Metas, progresso e vitória

## 10.1. Meta individual

O criador definirá a quantidade de dias válidos necessária para vencer.

Exemplo: meta de 30 dias.

Cada participante terá seu próprio contador, independentemente do progresso dos demais.

## 10.2. Progresso

O sistema exibirá:

- Dias válidos acumulados.
- Meta total.
- Percentual concluído.
- Dias restantes para atingir a meta.
- Sequência atual de dias consecutivos.
- Melhor sequência histórica.
- Data do último dia válido.

O percentual será calculado com base na quantidade de dias válidos em relação à meta, limitado a 100%.

## 10.3. Condição de vitória

O primeiro participante a alcançar a meta será declarado vencedor.

O backend deverá registrar a conclusão do desafio de maneira atômica, evitando que dois participantes sejam declarados vencedores por causa de processamento concorrente.

O registro de conclusão deverá incluir:

- Identificador do desafio.
- Identificador do vencedor.
- Momento em que a conclusão foi reconhecida.
- Quantidade de dias válidos.
- Referência ao evento ou dia que completou a meta.

O horário de processamento do webhook não deverá ser tratado automaticamente como o horário real do commit. A regra de desempate deverá considerar a atividade elegível e os timestamps confiáveis disponíveis.

Se dois participantes completarem a meta em um mesmo dia, o sistema utilizará o horário da atividade elegível que completou a meta. Caso não seja possível estabelecer uma ordem confiável, deverá aplicar uma regra de empate explicitamente definida, sem inventar uma ordem baseada apenas na chegada dos webhooks.

## 10.4. Regra proposta para dias consecutivos

Para o MVP, recomenda-se distinguir duas métricas:

- **Dias válidos acumulados:** determinam o cumprimento da meta e a vitória.
- **Streak atual:** quantidade de dias consecutivos com atividade válida.

Assim, perder um dia interrompe a sequência consecutiva, mas não apaga os dias válidos já acumulados.

Essa decisão evita que um único dia perdido invalide semanas de progresso. A regra deverá ser apresentada claramente antes do início de cada desafio.

## 10.5. Encerramento

Quando o primeiro participante atingir a meta:

- O desafio passará para o estado concluído.
- O vencedor será registrado.
- O ranking final será congelado.
- Os participantes poderão consultar os resultados.
- Novos eventos não poderão alterar retroativamente o vencedor sem um procedimento de correção auditável.

O criador poderá cancelar um desafio antes da conclusão. O cancelamento deverá ser registrado e comunicado aos membros.

---

# 11. Dashboard e experiência do usuário

## 11.1. Dashboard principal

O dashboard deverá apresentar:

- Grupos ativos.
- Progresso individual em cada grupo.
- Sequência atual.
- Próximas metas.
- Últimas atividades verificadas.
- Posição no ranking.
- Estado da integração GitHub.
- Convites pendentes, quando aplicável.

## 11.2. Página do grupo

Cada grupo terá uma página com:

- Nome e descrição.
- Meta individual.
- Modo de verificação.
- Participantes.
- Progresso individual.
- Ranking.
- Calendário de atividade.
- Data de início.
- Estado do desafio.
- Informações do vencedor, quando concluído.

## 11.3. Ranking

O ranking será ordenado principalmente pela quantidade de dias válidos.

Durante o desafio:

1. Participantes com mais dias válidos aparecem primeiro.
2. Se houver a mesma quantidade de dias, a posição poderá ser determinada pela sequência atual e, depois, pela atividade elegível mais recente, conforme a política de desempate definida para o produto.
3. A vitória será determinada pelo momento em que a meta for efetivamente alcançada, não pela posição temporária do ranking.

O sistema deverá diferenciar claramente a posição atual da condição de vencedor.

## 11.4. Calendário de atividade

O calendário deverá indicar:

- Dias válidos.
- Dias sem atividade.
- Dias futuros.
- Dias anteriores ao início do desafio.
- Períodos em que a verificação esteve indisponível, quando necessário.

O calendário não deverá exibir o código-fonte dos repositórios privados.

## 11.5. Feed de atividade

O feed exibirá eventos relevantes, como:

- Participante cumpriu a meta diária.
- Participante alcançou um marco.
- Participante atingiu a meta do desafio.
- Novo membro entrou no grupo.

O feed deverá priorizar o estado de progresso. Não será necessário reproduzir mensagens de commit ou detalhes de código.

---

# 12. Modelo de dados

O banco de dados será relacional, utilizando PostgreSQL.

As entidades abaixo representam a estrutura lógica inicial. Os nomes físicos poderão ser ajustados durante a implementação.

## 12.1. users

Armazena os usuários da plataforma.

Campos principais:

- id — UUID, chave primária.
- display_name.
- username.
- avatar_url.
- timezone.
- created_at.
- updated_at.
- status.

## 12.2. github_accounts

Armazena as contas GitHub vinculadas.

Campos principais:

- id — UUID.
- user_id — referência a users.
- github_user_id — identificador externo imutável.
- github_username.
- avatar_url.
- connection_status.
- encrypted_token ou referência ao mecanismo seguro de credenciais.
- token_expires_at, quando aplicável.
- connected_at.
- disconnected_at.

Restrições:

- Um usuário não deverá ter a mesma conta GitHub vinculada duas vezes.
- Tokens nunca deverão ser armazenados em texto puro.
- Tokens não deverão ser expostos ao frontend.

## 12.3. groups

Armazena os grupos.

Campos principais:

- id — UUID.
- name.
- description.
- creator_user_id.
- verification_mode.
- target_valid_days.
- timezone.
- status.
- starts_at.
- created_at.
- completed_at.

Valores sugeridos para verification_mode:

- SELECTED_REPOSITORIES.
- OWN_REPOSITORIES.

Valores sugeridos para status:

- DRAFT.
- ACTIVE.
- COMPLETED.
- CANCELLED.

## 12.4. group_members

Relaciona usuários e grupos.

Campos principais:

- id.
- group_id.
- user_id.
- role.
- joined_at.
- membership_status.
- progress_days.
- current_streak.
- best_streak.
- completed_at.

Restrições:

- Combinação group_id + user_id única.
- Um usuário não pode entrar duas vezes no mesmo grupo.
- O progresso armazenado deverá ser recalculável a partir do histórico validado.

## 12.5. group_repositories

Armazena os repositórios selecionados pelo grupo.

Campos principais:

- id.
- group_id.
- github_repository_id.
- owner_github_user_id ou identificador da organização.
- owner_login.
- repository_name.
- visibility.
- access_status.
- added_at.

Restrições:

- Máximo de três repositórios por grupo.
- Repositórios não poderão ser duplicados dentro do mesmo grupo.
- A lista ficará bloqueada após o início do desafio.

## 12.6. github_repositories

Catálogo de repositórios conhecidos e autorizados.

Campos principais:

- id.
- github_repository_id.
- owner_github_user_id.
- owner_login.
- repository_name.
- visibility.
- last_synced_at.
- access_status.

A identificação do repositório deverá utilizar o identificador externo do GitHub, não apenas seu nome.

## 12.7. github_events

Armazena eventos recebidos do GitHub.

Campos principais:

- id.
- github_event_id ou chave de idempotência.
- event_type.
- github_repository_id.
- payload_reference ou payload mínimo necessário.
- received_at.
- processed_at.
- processing_status.
- error_code.

Restrições:

- Eventos duplicados não podem gerar processamento duplicado.
- Payloads deverão ser minimizados e protegidos.
- O sistema não deverá armazenar código-fonte desnecessariamente.

## 12.8. eligible_commits

Armazena commits identificados como elegíveis.

Campos principais:

- id.
- github_repository_id.
- commit_sha.
- github_author_id, quando identificável.
- github_committer_id, quando identificável.
- committed_at.
- first_observed_at.
- eligibility_status.
- eligibility_reason.

Restrições:

- Um mesmo commit não deverá ser processado repetidamente como atividade nova.
- A elegibilidade deverá ser avaliada conforme o modo do grupo.
- A existência de um commit não significa automaticamente que ele seja elegível para todos os grupos.

## 12.9. member_daily_activity

Armazena os dias válidos por participante e desafio.

Campos principais:

- id.
- group_id.
- user_id.
- activity_date.
- valid_commit_count.
- first_eligible_commit_at.
- validation_status.
- validated_at.
- correction_reason, quando aplicável.

Restrição essencial:

- Combinação group_id + user_id + activity_date única.

Essa restrição é a principal garantia de que vários commits em um mesmo dia não gerem vários dias válidos.

## 12.10. challenge_results

Armazena o resultado final do desafio.

Campos principais:

- id.
- group_id.
- winner_user_id.
- achieved_at.
- winning_activity_date.
- result_status.
- finalized_at.

O banco deverá impedir que um desafio possua dois vencedores oficiais simultaneamente.

## 12.11. group_invites

Armazena convites.

Campos principais:

- id.
- group_id.
- token_hash.
- created_by_user_id.
- created_at.
- expires_at.
- revoked_at.
- usage_limit, quando aplicável.

O token original não deverá ser armazenado em texto puro se puder ser validado por hash.

## 12.12. audit_logs

Registra alterações importantes.

Campos principais:

- id.
- actor_user_id.
- entity_type.
- entity_id.
- action.
- metadata mínima.
- created_at.

Eventos relevantes:

- Criação e cancelamento de grupo.
- Alteração de configurações permitidas.
- Entrada e saída de membros.
- Revogação de integração.
- Correção de atividade.
- Finalização do desafio.

---

# 13. Arquitetura técnica

## 13.1. Stack proposta

### Frontend

- React.
- TypeScript.
- Vite.
- Tailwind CSS.
- shadcn/ui.
- Biblioteca de gerenciamento de estado conforme a necessidade.
- Cliente HTTP com tratamento centralizado de erros.

### Backend

- Java.
- Spring Boot.
- Spring Web.
- Spring Security.
- Spring Data JPA.
- Bean Validation.
- Flyway para migrações.
- Spring Boot Actuator para health checks e métricas.

### Banco de dados

- PostgreSQL.
- Índices para consultas por grupo, participante, data e identificadores externos.
- Restrições únicas para idempotência e integridade.
- Transações para atualização do progresso e definição do vencedor.

### Integrações

- GitHub App.
- Webhooks do GitHub.
- Provedor de e-mail, caso sejam necessárias notificações externas.
- Provedor de cobrança somente em uma etapa posterior.

### Infraestrutura

- Docker.
- Ambiente de desenvolvimento reproduzível.
- CI para testes e build.
- Deploy automatizado.
- Gestão segura de segredos.
- Logs estruturados e monitoramento.

## 13.2. Organização do backend

O backend deverá ser um monólito modular.

Módulos sugeridos:

1. identity.
2. github_integration.
3. groups.
4. challenges.
5. activity_validation.
6. leaderboard.
7. notifications.
8. audit.

Cada módulo terá responsabilidades claras e interfaces internas bem definidas.

O MVP não utilizará microsserviços. Filas e workers poderão ser introduzidos posteriormente se o volume de eventos justificar o desacoplamento.

## 13.3. Fluxo de processamento

1. GitHub envia um webhook.
2. Backend valida a assinatura.
3. Backend persiste o evento.
4. A idempotência é verificada.
5. O repositório é identificado.
6. O commit é avaliado.
7. A modalidade do grupo é aplicada.
8. A data local é calculada.
9. O dia válido é inserido ou atualizado de forma idempotente.
10. O progresso é recalculado.
11. A meta é verificada.
12. O resultado é finalizado atomicamente, quando aplicável.
13. O dashboard passa a exibir o novo estado.

Se o processamento falhar após o recebimento do webhook, o evento deverá permanecer recuperável para uma nova tentativa.

## 13.4. API inicial

Rotas ilustrativas:

**Autenticação e perfil**
- GET /api/v1/me
- PATCH /api/v1/me
- GET /api/v1/integrations/github
- DELETE /api/v1/integrations/github

**Grupos**
- POST /api/v1/groups
- GET /api/v1/groups
- GET /api/v1/groups/{groupId}
- PATCH /api/v1/groups/{groupId}
- POST /api/v1/groups/{groupId}/start
- POST /api/v1/groups/{groupId}/cancel
- GET /api/v1/groups/{groupId}/members

**Convites**
- POST /api/v1/groups/{groupId}/invites
- DELETE /api/v1/groups/{groupId}/invites/{inviteId}
- POST /api/v1/invites/{token}/accept

**Repositórios**
- GET /api/v1/github/repositories
- POST /api/v1/groups/{groupId}/repositories
- DELETE /api/v1/groups/{groupId}/repositories/{repositoryId}

**Progresso**
- GET /api/v1/groups/{groupId}/progress
- GET /api/v1/groups/{groupId}/leaderboard
- GET /api/v1/groups/{groupId}/activity
- GET /api/v1/groups/{groupId}/result

**Webhooks**
- POST /api/v1/webhooks/github

Essas rotas são uma proposta inicial. As permissões deverão ser verificadas em todas as operações protegidas.

---

# 14. Segurança e privacidade

## 14.1. Autorização

O backend será responsável por verificar se o usuário pode acessar cada grupo, desafio ou recurso.

Ocultar botões no frontend não será considerado controle de acesso suficiente.

## 14.2. Tokens

- Tokens serão armazenados de forma criptograficamente protegida.
- Segredos não serão registrados em logs.
- O acesso às credenciais será restrito ao backend.
- A revogação da integração deverá invalidar o uso futuro do token.
- Tokens serão excluídos quando não forem mais necessários, respeitando as obrigações aplicáveis.

## 14.3. Privacidade dos repositórios

A plataforma deverá armazenar somente os dados necessários para comprovar a atividade.

Não deverá:

- Exibir código-fonte privado.
- Compartilhar mensagens de commit privadas por padrão.
- Permitir que membros consultem arquivos privados de outros membros.
- Expor tokens ou dados de autenticação.
- Transformar a conexão GitHub em permissão para modificar repositórios.

## 14.4. Webhooks

- Validar assinatura antes de confiar no payload.
- Rejeitar eventos inválidos.
- Controlar tamanho de requisições.
- Evitar duplicidade.
- Implementar limites de requisições.
- Monitorar falhas de processamento.

## 14.5. LGPD

O produto deverá disponibilizar uma política de privacidade e informar quais dados são tratados, para quais finalidades e por quanto tempo.

Deverá oferecer meios para que o usuário solicite acesso, correção ou exclusão de seus dados, observadas as obrigações legais aplicáveis.

Também deverá estabelecer uma política para retenção de eventos, logs e resultados históricos.

---

# 15. Interface e design

## 15.1. Direção visual

A interface deverá transmitir:

- Tecnologia.
- Progresso.
- Competição saudável.
- Clareza.
- Consistência.

A proposta visual poderá utilizar um tema escuro como padrão, com referências a ferramentas de desenvolvimento, sem reproduzir integralmente a identidade visual de outros produtos.

## 15.2. Telas obrigatórias

1. Landing page.
2. Cadastro e login.
3. Conexão com GitHub.
4. Dashboard.
5. Criação de grupo.
6. Seleção de repositórios.
7. Convite e entrada em grupo.
8. Detalhes do desafio.
9. Ranking.
10. Calendário de atividade.
11. Perfil do usuário.
12. Configurações de integração.
13. Estado de desafio concluído.
14. Estados de erro e indisponibilidade.

## 15.3. Estados visuais

Cada tela deverá contemplar:

- Carregamento.
- Sucesso.
- Erro.
- Ausência de dados.
- Falta de permissão.
- Integração desconectada.
- Processamento pendente.
- Desafio concluído.
- Desafio cancelado.

O usuário deverá conseguir distinguir uma falha de integração de um dia efetivamente não cumprido.

---

# 16. Notificações

## 16.1. Escopo do MVP

Notificações internas poderão informar:

- Um participante cumpriu a meta diária.
- Um participante alcançou um marco.
- Um desafio foi concluído.
- Um convite foi aceito.
- A integração GitHub precisa de atenção.

Lembretes automáticos por e-mail ou outros canais poderão ser adicionados após a validação do núcleo do produto.

## 16.2. Regras

- Evitar notificações duplicadas.
- Respeitar preferências do usuário.
- Não enviar conteúdo privado de repositórios.
- Permitir desativar notificações não essenciais.

---

# 17. Requisitos não funcionais

## 17.1. Confiabilidade

O processamento deverá ser idempotente e tolerar reentrega de eventos.

## 17.2. Integridade

O banco deverá garantir que:

- Um participante tenha no máximo um registro de dia válido por desafio e data.
- Um desafio tenha no máximo um vencedor oficial.
- Um repositório não seja vinculado duas vezes ao mesmo grupo.
- O progresso não seja incrementado duas vezes por causa de eventos duplicados.

## 17.3. Desempenho

O dashboard deverá consultar dados agregados, evitando buscar todo o histórico de commits a cada carregamento.

Os endpoints mais acessados deverão utilizar índices adequados.

## 17.4. Disponibilidade

A aplicação deverá tratar falhas do GitHub de maneira controlada e recuperar eventos quando possível.

Não será necessário implementar alta disponibilidade distribuída no MVP, mas o banco e a aplicação deverão possuir estratégia de backup e recuperação.

## 17.5. Observabilidade

Monitorar:

- Quantidade de webhooks recebidos.
- Quantidade de eventos processados.
- Falhas de validação.
- Latência do processamento.
- Falhas de integração.
- Erros de autenticação.
- Tempo de resposta da API.
- Quantidade de correções de atividade.
- Custos e limites de infraestrutura.

## 17.6. Testes

O projeto deverá possuir:

- Testes unitários das regras de elegibilidade.
- Testes de integração com PostgreSQL.
- Testes de idempotência.
- Testes de concorrência na definição do vencedor.
- Testes de autorização.
- Testes do processamento de webhooks.
- Testes de reconciliação.
- Testes ponta a ponta dos fluxos principais.

---

# 18. Critérios de aceite do MVP

## Autenticação e integração

- O usuário consegue autenticar-se.
- O usuário consegue conectar sua conta GitHub.
- O backend identifica corretamente a conta externa.
- O sistema consegue receber e validar webhooks.
- Tokens não são expostos ao navegador.

## Grupos

- Um usuário consegue criar um grupo.
- O grupo possui meta individual.
- O criador consegue compartilhar um convite.
- Um participante consegue entrar no grupo.
- Participantes não autorizados não conseguem consultar dados privados.

## Repositórios selecionados

- O criador consegue vincular até três repositórios.
- Um quarto repositório é rejeitado.
- Repositórios não selecionados não geram dias válidos.
- A lista não pode ser alterada após o início do desafio.

## Repositórios próprios

- Cada participante é avaliado individualmente.
- Repositórios pertencentes à própria conta podem gerar atividade elegível.
- Repositórios pertencentes à empresa não são elegíveis pelo critério de propriedade.
- Repositórios de terceiros não são elegíveis.
- A aplicação não confunde o autor do commit com o proprietário do repositório.

## Progresso

- Um commit elegível gera no máximo um dia válido por data.
- Vários commits no mesmo dia não aumentam a contagem diária.
- Eventos repetidos não duplicam o progresso.
- Commits anteriores ao início não são contabilizados.
- O calendário corresponde ao histórico validado.
- O progresso pode ser recalculado sem gerar duplicidade.

## Vitória

- O participante que alcançar a meta primeiro é declarado vencedor.
- A conclusão é registrada de forma atômica.
- Não há dois vencedores oficiais no mesmo desafio.
- O resultado final fica disponível aos participantes.
- Correções posteriores possuem registro de auditoria.

## Segurança

- A assinatura dos webhooks é validada.
- As permissões são verificadas no backend.
- Tokens não aparecem em logs.
- Dados privados de repositórios não são exibidos a outros participantes.
- A desconexão da integração é tratada corretamente.

---

# 19. Plano de implementação

## Fase 1 — Fundação

- Configurar repositório e estrutura do projeto.
- Configurar React, TypeScript e Spring Boot.
- Configurar PostgreSQL e Flyway.
- Preparar Docker para desenvolvimento.
- Configurar pipeline de testes e build.
- Definir padrões de logs e tratamento de erros.

**Entrega:** aplicação executável com estrutura de backend, frontend e banco.

## Fase 2 — Autenticação e GitHub

- Implementar cadastro e login.
- Implementar conexão com GitHub.
- Configurar GitHub App.
- Implementar gestão segura de tokens.
- Implementar consulta de repositórios.
- Implementar recebimento e validação de webhooks.

**Entrega:** usuário autenticado com integração GitHub funcional.

## Fase 3 — Grupos e desafios

- Implementar criação de grupos.
- Implementar convites.
- Implementar entrada e saída de participantes.
- Implementar configuração da meta.
- Implementar seleção do modo.
- Implementar vinculação de até três repositórios.
- Implementar início e cancelamento de desafios.

**Entrega:** grupos funcionais com regras configuráveis.

## Fase 4 — Validação e progresso

- Implementar elegibilidade dos commits.
- Implementar idempotência.
- Implementar contagem de dias válidos.
- Implementar fuso horário.
- Implementar streaks.
- Implementar reconciliação.
- Implementar cálculo de progresso.
- Implementar definição atômica do vencedor.

**Entrega:** núcleo funcional do produto.

## Fase 5 — Interface social

- Implementar dashboard.
- Implementar página do grupo.
- Implementar calendário.
- Implementar ranking.
- Implementar feed de atividade.
- Implementar estados de erro e integração desconectada.
- Implementar tela de desafio concluído.

**Entrega:** experiência utilizável do início ao fim.

## Fase 6 — Estabilização e piloto

- Executar testes unitários e de integração.
- Testar duplicidade de webhooks.
- Testar concorrência na vitória.
- Testar permissões e privacidade.
- Testar reconciliação.
- Configurar monitoramento e backups.
- Liberar para grupos-piloto.
- Coletar métricas de uso e retenção.

**Entrega:** MVP pronto para validação com usuários reais.

---

# 20. Fora do escopo do MVP

Os seguintes recursos ficam para versões futuras:

- Assinaturas pagas e planos comerciais.
- Cobrança por usuário ou grupo.
- Desafios públicos e descoberta de grupos.
- Chat interno.
- Feed social avançado.
- Reações e comentários.
- Notificações push.
- Integrações com GitLab e Bitbucket.
- Pontuação por qualidade de código.
- Pontuação baseada em horas estudadas.
- Desafios corporativos.
- Aplicativos nativos para iOS e Android.
- Microsserviços.
- Kubernetes.
- Filas distribuídas, salvo necessidade operacional comprovada.
- Integração com plataformas de cursos.
- Modos avançados de competição.

---

# 21. Riscos e mitigação

## 21.1. Commits artificiais

**Risco:** participantes podem criar commits vazios ou triviais apenas para cumprir a meta.

**Mitigação:** excluir commits vazios, aplicar critérios transparentes e evitar premiar volume de commits. Não prometer que a plataforma consegue provar o aprendizado.

## 21.2. Webhooks perdidos ou atrasados

**Risco:** o usuário realiza uma atividade válida, mas o sistema demora para reconhecê-la.

**Mitigação:** processamento idempotente, tentativas posteriores, reconciliação e estado de verificação transparente.

## 21.3. Limites da API do GitHub

**Risco:** excesso de consultas durante a sincronização de repositórios.

**Mitigação:** webhooks como mecanismo principal, consultas incrementais, cache de metadados e respeito aos limites da API.

## 21.4. Repositórios privados

**Risco:** exposição indevida de dados ou solicitação de permissões excessivas.

**Mitigação:** permissões mínimas, autorização por recurso, armazenamento de metadados mínimos e ausência de exposição de código-fonte.

## 21.5. Disputas sobre vencedores

**Risco:** dois participantes atingirem a meta em horários próximos ou eventos chegarem fora de ordem.

**Mitigação:** definição explícita de desempate, persistência dos eventos, transações atômicas e auditoria.

## 21.6. Baixa retenção

**Risco:** o usuário perde interesse após os primeiros dias.

**Mitigação:** desafios com metas alcançáveis, acompanhamento visual, competição saudável e análise de retenção por grupo.

---

# 22. Monetização futura

A monetização não será implementada no MVP, mas o produto deverá evitar decisões que impeçam sua inclusão posterior.

Possíveis modelos:

- Plano gratuito para pequenos grupos.
- Plano premium com mais grupos e desafios simultâneos.
- Estatísticas históricas avançadas.
- Desafios públicos e recursos de comunidade.
- Ferramentas de gestão de desafios para comunidades de programação.
- Planos para bootcamps e instituições de ensino.

A estratégia comercial deverá ser validada com dados de utilização antes da implementação de cobrança.

---

# 23. Decisões de produto consolidadas

1. A plataforma será um SaaS web para desenvolvedores.
2. A integração principal será com o GitHub.
3. Os grupos terão desafios competitivos com metas individuais.
4. O criador definirá a quantidade de dias válidos necessária para vencer.
5. Cada dia exigirá pelo menos um commit elegível.
6. A quantidade de commits no mesmo dia não aumentará o número de dias válidos.
7. O primeiro participante a atingir a meta será o vencedor.
8. Haverá um modo com até três repositórios selecionados pelo grupo.
9. Haverá um modo que utiliza os repositórios próprios de cada participante.
10. No modo pessoal, repositórios pertencentes à empresa não serão elegíveis pelo critério de propriedade.
11. O backend será responsável pela validação e pelo resultado oficial.
12. A aplicação não deverá expor código-fonte privado.
13. O MVP será implementado como monólito modular.
14. Cobrança e funcionalidades sociais avançadas ficarão para versões posteriores.

---

# 24. Decisões pendentes antes da implementação

As seguintes definições deverão ser confirmadas durante o refinamento técnico:

- Quantidade máxima de participantes por grupo.
- Se a meta conta dias válidos acumulados ou exige dias consecutivos.
- Se o início do desafio será imediato ou agendado.
- Política de empate quando os timestamps não permitirem estabelecer uma ordem confiável.
- Tratamento definitivo de forks e commits automatizados.
- Política de saída de participantes durante um desafio.
- Política de cancelamento e correção de resultados.
- Período de retenção dos eventos recebidos.
- Provedor e modelo de autenticação.
- Infraestrutura definitiva de produção.
- Regras e limites de uso do plano gratuito futuro.

Essas decisões deverão ser registradas como configurações e regras explícitas, evitando comportamentos implícitos no código.

---

# 25. Definição de pronto

O MVP será considerado pronto quando um usuário conseguir:

1. Criar uma conta.
2. Conectar o GitHub.
3. Criar um grupo.
4. Selecionar um dos dois modos de verificação.
5. Definir uma meta individual.
6. Convidar amigos.
7. Iniciar o desafio.
8. Ter commits elegíveis detectados e validados automaticamente.
9. Visualizar dias válidos, streaks e ranking.
10. Acompanhar o progresso dos participantes.
11. Ter um vencedor identificado quando alguém alcançar a meta.
12. Consultar o resultado final do desafio.

Todos esses fluxos deverão funcionar com testes automatizados, controles de autorização, integridade dos dados e tratamento de falhas da integração.

**Resultado esperado:** uma primeira versão funcional que permita validar a hipótese central do DevStreak: se a competição social, combinada com a verificação automática de atividade no GitHub, incentiva desenvolvedores a manter constância nos estudos e projetos.

