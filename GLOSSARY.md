# Moneyment

A personal money log for one person on one phone. It records money spent and earned so the person can review it later.

## Language

**Transaction**:
A single record of money spent or earned on a given day. It is either an Expense or an Income.
_Avoid_: Entry, movement, operation, record

**Expense**:
A Transaction where money leaves the person.
_Avoid_: Spending, payment, outgoing, debit

**Income**:
A Transaction where money reaches the person.
_Avoid_: Earning, revenue, incoming, credit

**Amount**:
The positive quantity of euros of a Transaction.
_Avoid_: Value, price, total

**Category**:
The kind of thing a Transaction was for, such as groceries or salary. Expenses and Incomes have separate Categories, and Categories have no subcategories.
_Avoid_: Tag, type, group, label

**Account**:
The place the money left on an Expense or arrived on an Income, such as cash, a card, or a bank account. It is a label only; it carries no balance.
_Avoid_: Payment method, wallet, source, pot

**Archive**:
To retire a Category or Account so it can no longer be chosen for new Transactions while staying on existing ones.
_Avoid_: Delete, hide, disable

**Note**:
Optional free text the person attaches to a Transaction.
_Avoid_: Description, comment, memo
