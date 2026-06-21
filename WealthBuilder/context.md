Build a personal finance tracking application that helps users understand, control, and optimize their spending habits. Unlike complex accounting systems, this application should focus on clarity, simplicity, and everyday financial awareness – allowing users to easily track income, expenses, and monthly budgets.

You will create a modern desktop or web application that enables users to record transactions, analyze spending patterns, and monitor their financial balance over time.

Note: Choose a creative name for your application that reflects budgeting, balance, or financial awareness!


Users should be able to:

· Add, edit, and delete income transactions

· Add, edit, and delete expense transactions

· Categorize transactions (e.g. Food, Rent, Transport, Entertainment, Utilities)

· Set a monthly budget

· View current balance and monthly summary

· Visualize expenses using charts

· Filter transactions by date and category

· Enjoy using your application

By completing this project, you will:

· Build a data-driven application with real business logic

· Work with numbers, dates, and financial calculations

· Practice local data persistence (files or database)

· Design meaningful dashboards and summaries

· Implement filtering, aggregation, and reporting

· Improve validation and error handling skills

Detailed functional requirements:

· Provide a main dashboard that shows:

o Current balance

o Total income for the selected month

o Total expenses for the selected month

o Remaining budget

· Implement transaction creation with the following fields:

o Title

o Amount

o Type (income or expense)

o Category

o Date

o Optional note

· Allow transactions to be edited and deleted at any time.

· Allow users to define and update a monthly budget.

· Implement automatic calculation of:

o Monthly totals

o Daily averages

o Remaining budget

· Implement filtering by:

o Date range

o Category

o Transaction type

· Implement visualizations such as:

o Pie chart for category distribution

o Bar or line chart for monthly trends

· Create a monthly report view with:

o All transactions

o Totals per category

o Balance summary

· Ensure that all data is stored locally and persists between application restarts.

Non-functional requirements:

· The application should feel fast and responsive

· Calculations must always be accurate and consistent

· The UI should remain clean and readable even with many records

· The application should not freeze during large data operations

· Data should be saved safely without corruption

· The layout should adapt well to different screen sizes (if web-based)

· Code should be clean, modular, maintainable, and well-documented

Important notes:

· You will NOT implement this application – use it solely as a reference to improve your planning and AI-assisted development workflow

· The goal is to practice business logic, data modeling, and structured development with AI as a coding assistant

----

# Fleshed out ideas:

- Users with autnethication, everything except login and register are auth required. 

- There should be a role functionality, with one single role moderator

- Commodity entity that only moderators can create and edit (name, desc, image)
(for example: Stocks, Cryptocurrencies, Precious metals)

- Normal users should have a balance and on the home page they should be able to see a beautiful carousel of the commodities available.

- They should be able to click a given commodity and open its details page. There they should be able to see a table of columns: name, amount, date, quantity.
They should be able to create, update, delete entries. Somewhere below there should be an aggregation by the commodity name to see totals: for example if we open the commodity for stocks we should wee: average price, period(from first to last transaction dates), quantity sum, amount sum.