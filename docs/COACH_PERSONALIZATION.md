# Coach context and personalization

Coach answers the current question first. Recent replies are part of its context, so unchanged sleep statistics and recovery summaries should not be repeated unless the user asks for them or they are necessary for the new answer. A new question does not automatically require another health recap or another recommendation.

## Analysis without UI overload

Android prepares a bounded snapshot of the signed-in user's completed personal sleep models and observed sleep-timing comparisons. Every ordinary Coach question can attach this snapshot in the optional `sleep_analysis` request field; opening the insight screen is not required. A question launched from a historical insight carries that specific dated snapshot instead. Model parameters and importance values are message metadata, not visible user-message text.

The backend validates this closed schema, stores it in that user's message metadata and includes only the latest question's snapshot in the answer context. It labels the snapshot's relationship to the latest server sleep date and derives recent-validation quality separately for each outcome from sample counts and model/reference errors. Mixed quality is not generalized across outcomes. A weak model and negative importance are retained as exploratory evidence. They do not identify a cause, an effect direction, or the explanation for an individual night. Coach can still use observed records and relevant hypotheses, while stating uncertainty when it matters.

The app shows `线索还不稳定` when the model does not improve on the simple reference. Its model-detail panel starts collapsed. The Coach handoff remains a short editable question; the underlying analysis travels separately.

## Personal understanding across conversations

Each authenticated user's own health database contains `coach_memory` and an update history. Coach receives a bounded selection of remembered goals, routines, preferences, constraints, context and response-style preferences, plus related excerpts from that same user's other conversations. User-stated information is distinguished from tentative understanding. Past assistant replies remain labeled as assistant replies and do not become confirmed personal facts.

A completed answer may propose up to eight memory updates in an internal JSON envelope. Only the natural-language answer is displayed or stored as assistant message content. Updates require valid source IDs from actual user messages in the same database, including the current user message. Corrections replace a stable key; deleted keys are kept as tombstones so older conversation excerpts do not immediately recreate them. Transient device readings should remain dated health data rather than permanent identity facts.

The settings section `Coach 记住了什么` lists active memories and allows the user to forget an individual item. Users can also correct or request forgetting in conversation. No memory is shared between accounts. There is no historical bulk import: personal understanding grows from ongoing conversations while relevant older messages can supply context.

Memory is capped at 120 active items, with up to 40 selected for a response and up to six related historical excerpts. Retrieval uses Unicode word overlap, Chinese character pairs, category and recency; it is not an embedding search. Model-generated suggestions that fail validation must not discard an otherwise usable answer.

Context is provided inline with the request instructions, so Coach does not depend on filesystem tool availability to read its question or evidence. A private per-turn context file is retained only during execution.

Validation includes per-user and cross-thread isolation, source validation, update/delete tombstones, multilingual retrieval, snapshot quality/freshness, hidden metadata, and malformed output envelopes. A real ACP run against an isolated synthetic profile persisted preferences and reused them in another thread while answering a changed question without repeating prior sleep statistics.
