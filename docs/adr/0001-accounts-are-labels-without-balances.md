# Accounts are labels without balances

Every Transaction names the Account the money left or reached, but the app keeps no balance per Account. We chose this because the app is a log, not a ledger: tracking balances would require a starting balance per Account and a third kind of Transaction (a transfer between Accounts, such as a cash withdrawal), which adds steps to logging and a reconciliation burden for the person.

## Consequences

- Moving money between the person's own Accounts is not logged at all.
- The app cannot answer "how much money do I have in this Account?". It can only answer "how much went through this Account?".
- Adding balances later means introducing transfers and asking the person for starting balances; existing Transactions stay valid.
