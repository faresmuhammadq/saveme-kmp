// Talks to the local saveme-backend from inside a flow, as a second device ("slot") of the same
// account. Used to set up server-side Givens (another device signed in, a profile saved
// elsewhere) and to check server-side Thens (a session really ended, a password really changed).
//
// Call it with:
//   - runScript:
//       file: ../common/api.js
//       env:
//         ACTION: login          # see the switch below
//         SLOT: B                # names the session; its token is kept in output.token_B
//
// Every action throws on an unexpected status, which fails the flow at that step.
// E2E_API, E2E_EMAIL and E2E_PASSWORD come from run-e2e.sh.

function arg(name, fallback) {
    // Unset env vars are not defined at all in Maestro's JS scope. Never declare a local with
    // the same name as an env var: `var` hoisting would shadow it with undefined.
    var value
    try { value = eval(name) } catch (e) { value = undefined }
    return value === undefined || value === null ? fallback : value
}

var apiBase = arg('E2E_API', 'http://127.0.0.1:43871') + '/api/v1/'
var slot = arg('SLOT', 'B')

function headers(token) {
    var h = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Client-Platform': 'android',
        'X-Client-Version': '1.0.0',
    }
    if (token) h['Authorization'] = 'Bearer ' + token
    return h
}

function uuid7() {
    var hex = Date.now().toString(16).padStart(12, '0')
    for (var i = 0; i < 20; i++) hex += Math.floor(Math.random() * 16).toString(16)
    var variant = (8 + Math.floor(Math.random() * 4)).toString(16)
    return hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-7' + hex.slice(13, 16) + '-' + variant +
        hex.slice(17, 20) + '-' + hex.slice(20, 32)
}

function call(method, path, body, token) {
    var options = { headers: headers(token) }
    if (body !== undefined) options.body = JSON.stringify(body)
    var r
    switch (method) {
        case 'GET': r = http.get(apiBase + path, options); break
        case 'POST': r = http.post(apiBase + path, options); break
        case 'PUT': r = http.put(apiBase + path, options); break
        case 'DELETE': r = http.delete(apiBase + path, options); break
        // Maestro's http.delete sends no body; wallets are deleted with {"version": n} in the body.
        case 'DELETE_WITH_BODY': options.method = 'DELETE'; r = http.request(apiBase + path, options); break
        // Transactions are restored with PATCH {"deleted_at": null, "version": n} (backend spec 012).
        case 'PATCH': options.method = 'PATCH'; r = http.request(apiBase + path, options); break
    }
    return { status: r.status, body: r.body ? json(r.body) : null }
}

function expect(r, status, what) {
    if (r.status !== status) {
        throw new Error(what + ': expected HTTP ' + status + ', got ' + r.status + ' ' + JSON.stringify(r.body))
    }
    return r
}

function token() {
    var t = output['token_' + slot]
    if (!t) throw new Error('slot ' + slot + ' is not signed in; run ACTION login first')
    return t
}

function login(email, password, name) {
    return call('POST', 'auth/login', {
        email: email,
        password: password,
        device: {
            session_id: uuid7(),
            device_id: 'e2e-' + slot.toLowerCase(),
            name: name,
            platform: 'android',
            app_version: '1.0.0',
        },
    })
}

// Wallets (spec 006). A wallet is found by ID, or by its WALLET name among the caller's wallets.
function listAllWallets() {
    return expect(call('GET', 'wallets?limit=50', undefined, token()), 200, 'listWallets').body.data
}

function findWallet() {
    var id = arg('ID')
    if (id) return expect(call('GET', 'wallets/' + id, undefined, token()), 200, 'getWallet').body.data
    var name = arg('WALLET')
    var matches = listAllWallets().filter(function (w) { return w.name === name })
    if (matches.length !== 1) throw new Error('findWallet: expected one wallet named "' + name + '", found ' + matches.length)
    return matches[0]
}

function putWallet(w, changes) {
    var body = {
        type: w.type,
        name: changes.name !== undefined ? changes.name : w.name,
        currency: w.currency,
        opening_balance_minor: changes.opening !== undefined ? changes.opening : w.opening_balance_minor,
        icon: w.icon,
        color: w.color,
        version: w.version,
    }
    return expect(call('PUT', 'wallets/' + w.id, body, token()), 200, 'saveWallet').body.data
}

// Categories (spec 007). A category is found by ID, or by its CATEGORY name among the caller's own.
function listAllCategories() {
    return expect(call('GET', 'categories?limit=200', undefined, token()), 200, 'listCategories').body.data
}

function findCategory() {
    var id = arg('ID')
    var all = listAllCategories()
    var name = arg('CATEGORY')
    var matches = all.filter(function (c) { return id ? c.id === id : c.name === name })
    if (matches.length !== 1) throw new Error('findCategory: expected one category ' + (id || '"' + name + '"') + ', found ' + matches.length)
    return matches[0]
}

function putCategory(c, changes) {
    var body = {
        kind: c.kind,
        name: changes.name !== undefined ? changes.name : c.name,
        icon: changes.icon !== undefined ? changes.icon : c.icon,
        color: changes.color !== undefined ? changes.color : c.color,
        version: c.version,
    }
    return expect(call('PUT', 'categories/' + c.id, body, token()), 200, 'saveCategory').body.data
}

// Transactions (spec 008). The wallet is WALLET_ID, or WALLET (a name), or output.walletId from a createWallet in this run;
// a transaction is found by ID. A category is found by name among the caller's own, then among the default set.
function transactionWalletId() {
    var id = arg('WALLET_ID')
    if (id) return id
    if (arg('WALLET')) return findWallet().id
    if (output.walletId) return output.walletId
    throw new Error('transactions: no WALLET_ID, no WALLET and no wallet created by this script run')
}

function listAllTransactions(walletId) {
    return expect(call('GET', 'wallets/' + walletId + '/transactions?limit=200', undefined, token()), 200, 'listTransactions').body.data
}

function findTransaction(walletId) {
    var id = arg('ID')
    if (id) {
        var matches = listAllTransactions(walletId).filter(function (t) { return t.id === id })
        if (matches.length !== 1) throw new Error('findTransaction: no transaction ' + id + ' in wallet ' + walletId)
        return matches[0]
    }
    // Without an ID: the one transaction whose note is NOTE.
    var byNote = listAllTransactions(walletId).filter(function (t) { return t.description === arg('NOTE') })
    if (byNote.length !== 1) throw new Error('findTransaction: expected one transaction with note "' + arg('NOTE') + '", found ' + byNote.length)
    return byNote[0]
}

function resolveCategoryId() {
    var id = arg('CATEGORY_ID')
    if (id) return id
    var name = arg('CATEGORY')
    if (!name) return null
    var own = listAllCategories().filter(function (c) { return c.name === name })
    if (own.length === 1) return own[0].id
    var defaults = expect(call('GET', 'platform-categories', undefined, token()), 200, 'listPlatformCategories').body.data
    var found = defaults.filter(function (c) { return c.names.en === name })
    if (found.length < 1) throw new Error('resolveCategoryId: no category named "' + name + '"')
    return found[0].id
}

// Today's calendar date on this machine (YYYY-MM-DD), the default date of a seeded transaction, so
// "Today" and "This month" hold on any day the flows run (the device and the host share a time zone).
function todayIso() {
    var d = new Date()
    var mm = (d.getMonth() + 1 < 10 ? '0' : '') + (d.getMonth() + 1)
    var dd = (d.getDate() < 10 ? '0' : '') + d.getDate()
    return d.getFullYear() + '-' + mm + '-' + dd
}

// DATE, or today when it is unset or empty.
function dateArg() {
    var value = arg('DATE')
    return value ? value : todayIso()
}

function transactionBody(t, changes) {
    return {
        kind: t.kind,
        date: changes.date !== undefined ? changes.date : t.date,
        amount_minor: changes.amount !== undefined ? changes.amount : t.amount_minor,
        currency: t.currency,
        category_id: t.category_id,
        counter_wallet_id: t.counter_wallet_id,
        description: changes.note !== undefined ? changes.note : t.description,
        memo: changes.memo !== undefined ? changes.memo : t.memo,
        counterparty: changes.person !== undefined ? changes.person : t.counterparty,
        cleared: t.cleared,
        version: t.version,
    }
}

// Recurring rules (spec 010). The wallet is WALLET_ID, or WALLET (a name), or output.walletId; a rule is found by RULE_ID,
// or output.ruleId from a createRule in this run. Occurrences are server-made: the script only lists, confirms and materialises.
function ruleId() {
    var id = arg('RULE_ID') ? arg('RULE_ID') : output.ruleId
    if (!id) throw new Error('recurring: no RULE_ID and no rule created by this script run')
    return id
}

function listAllOccurrences(walletId) {
    var all = expect(call('GET', 'wallets/' + walletId + '/occurrences?limit=200', undefined, token()), 200, 'listOccurrences').body.data
    return all.filter(function (o) { return !arg('RULE_ID') && !output.ruleId ? true : o.recurring_rule_id === ruleId() })
}

// The whole RecurringRuleWrite, as the app sends it (21 members, explicit nulls).
function ruleBody(w, version) {
    var start = arg('START') ? arg('START') : todayIso()
    if (arg('START_IN_DAYS')) {
        var shifted = new Date()
        shifted.setDate(shifted.getDate() + parseInt(arg('START_IN_DAYS')))
        start = shifted.getFullYear() + '-' + (shifted.getMonth() + 1 < 10 ? '0' : '') + (shifted.getMonth() + 1) + '-' + (shifted.getDate() < 10 ? '0' : '') + shifted.getDate()
    }
    var frequency = arg('FREQUENCY', 'monthly')
    var monthly = frequency === 'monthly' || frequency === 'yearly'
    return {
        preset: arg('PRESET', 'custom'),
        status: arg('STATUS', 'active'),
        kind: arg('KIND', 'expense'),
        amount_minor: parseInt(arg('AMOUNT', '450000')),
        amount_basis: arg('BASIS', 'per_occurrence'),
        currency: w.currency,
        category_id: arg('KIND', 'expense') === 'transfer' ? null : resolveCategoryId(),
        counter_wallet_id: arg('KIND', 'expense') === 'transfer' ? arg('COUNTER_WALLET_ID') : null,
        description: arg('DESCRIPTION', null),
        memo: null,
        counterparty: null,
        auto_confirm: arg('AUTO_CONFIRM', 'false') === 'true',
        frequency: frequency,
        interval: parseInt(arg('INTERVAL', '1')),
        anchor_day: monthly ? parseInt(arg('ANCHOR_DAY', String(parseInt(start.slice(8, 10))))) : null,
        start_date: start,
        end_date: arg('END') ? arg('END') : null,
        occurrence_count: arg('COUNT') ? parseInt(arg('COUNT')) : null,
        payout_round: arg('PAYOUT_ROUND') ? parseInt(arg('PAYOUT_ROUND')) : null,
        payout_category_id: arg('PAYOUT_CATEGORY_ID') ? arg('PAYOUT_CATEGORY_ID') : null,
        version: version,
    }
}

// The whole OccurrenceWrite, as the app sends it (nine members); CHANGES overrides.
function occurrenceBody(o, changes) {
    var body = {
        status: o.status,
        date: o.date,
        amount_minor: o.amount_minor,
        category_id: o.category_id,
        description: o.description,
        memo: o.memo,
        counterparty: o.counterparty,
        transaction_id: o.transaction_id,
        version: o.version,
    }
    for (var key in changes) body[key] = changes[key]
    return body
}

var email = arg('EMAIL', arg('E2E_EMAIL'))
var password = arg('PASSWORD', arg('E2E_PASSWORD'))

switch (arg('ACTION')) {
    // Signs this slot in as a device named DEVICE_NAME (default "e2e-device-<slot>").
    case 'login':
        var r = expect(login(email, password, arg('DEVICE_NAME', 'e2e-device-' + slot.toLowerCase())), 200, 'login')
        output['token_' + slot] = r.body.data.credential
        break

    // Checks that EMAIL / PASSWORD can (EXPECT=200) or cannot (EXPECT=401, 423…) sign in.
    case 'expectLogin':
        expect(login(email, password, 'e2e-probe'), parseInt(arg('EXPECT', '200')), 'expectLogin')
        break

    // Checks that this slot's session is still valid (EXPECT=200) or was ended (EXPECT=401).
    case 'expectSession':
        expect(call('GET', 'me', undefined, token()), parseInt(arg('EXPECT', '200')), 'expectSession')
        break

    // Checks how many sessions of the account are active (COUNT), seen from this slot.
    case 'expectSessionCount':
        var sessions = expect(call('GET', 'auth/sessions', undefined, token()), 200, 'sessions').body.data
        var active = sessions.filter(function (s) { return s.state === 'active' }).length
        if (active !== parseInt(arg('COUNT'))) {
            throw new Error('expectSessionCount: expected ' + arg('COUNT') + ' active sessions, got ' + active)
        }
        break

    // Ends every session except this slot's own (for example the phone's).
    case 'endOtherSessions':
        expect(call('DELETE', 'auth/sessions', undefined, token()), 204, 'endOtherSessions')
        break

    // Saves the profile from this slot. Fields not given keep their current values.
    // DISPLAY_NAME="" clears the name.
    case 'saveProfile':
        var p = expect(call('GET', 'me', undefined, token()), 200, 'me').body.data.profile
        var displayName = arg('DISPLAY_NAME', p.display_name)
        expect(call('PUT', 'me/profile', {
            display_name: displayName === '' ? null : displayName,
            locale: arg('LOCALE', p.locale),
            timezone: arg('TIMEZONE', p.timezone),
            default_currency: arg('CURRENCY', p.default_currency),
            budget_period_start_day: parseInt(arg('BUDGET_DAY', String(p.budget_period_start_day))),
            version: p.version,
        }, token()), 200, 'saveProfile')
        break

    // Copies account and profile fields into output.account / output.profile for assertTrue.
    case 'me':
        var d = expect(call('GET', 'me', undefined, token()), 200, 'me').body.data
        output.account = d.account
        output.profile = d.profile
        break

    // Schedules deletion from this slot.
    case 'requestDeletion':
        expect(call('POST', 'account/deletion-request', { password: password }, token()), 200, 'requestDeletion')
        break

    // Creates a wallet from this slot: NAME, TYPE (cash), CURRENCY (EGP), OPENING in minor units (10000).
    // Copies it into output.wallet and its id into output.walletId.
    case 'createWallet':
        var id = uuid7()
        var created = expect(call('PUT', 'wallets/' + id, {
            type: arg('TYPE', 'cash'),
            name: arg('NAME', 'API wallet'),
            currency: arg('CURRENCY', 'EGP'),
            opening_balance_minor: parseInt(arg('OPENING', '10000')),
            icon: null,
            color: null,
            version: 0,
        }, token()), 201, 'createWallet').body.data
        output.wallet = created
        output.walletId = created.id
        break

    // Copies one wallet (by ID or WALLET name) into output.wallet for assertTrue.
    case 'getWallet':
        output.wallet = findWallet()
        break

    // Copies the caller's wallets into output.wallets and their number into output.walletCount.
    case 'listWallets':
        output.wallets = listAllWallets()
        output.walletCount = output.wallets.length
        break

    // Saves a wallet from this slot: NEW_NAME and/or OPENING (minor units).
    case 'saveWallet':
        var current = findWallet()
        var changes = {}
        if (arg('NEW_NAME') !== undefined) changes.name = arg('NEW_NAME')
        if (arg('OPENING') !== undefined) changes.opening = parseInt(arg('OPENING'))
        output.wallet = putWallet(current, changes)
        break

    // Archives a wallet from this slot.
    case 'archiveWallet':
        var toArchive = findWallet()
        output.wallet = expect(call('POST', 'wallets/' + toArchive.id + '/archive', { version: toArchive.version }, token()), 200, 'archiveWallet').body.data
        break

    // Deletes a wallet from this slot (JSON body on DELETE, backend spec 010).
    case 'deleteWallet':
        var toDelete = findWallet()
        expect(call('DELETE_WITH_BODY', 'wallets/' + toDelete.id, { version: toDelete.version }, token()), 200, 'deleteWallet')
        break

    // Copies the caller's own categories into output.categories and their number into output.categoryCount.
    case 'listCategories':
        output.categories = listAllCategories()
        output.categoryCount = output.categories.length
        break

    // Copies one category (by ID or CATEGORY name) into output.category for assertTrue.
    case 'getCategory':
        output.category = findCategory()
        break

    // Creates a category from this slot: NAME, KIND (expense), ICON and COLOR (none by default).
    // Copies it into output.category and its id into output.categoryId.
    case 'createCategory':
        var categoryId = uuid7()
        var newCategory = expect(call('PUT', 'categories/' + categoryId, {
            kind: arg('KIND', 'expense'),
            name: arg('NAME', 'API category'),
            icon: arg('ICON', null),
            color: arg('COLOR', null),
            version: 0,
        }, token()), 201, 'createCategory').body.data
        output.category = newCategory
        output.categoryId = newCategory.id
        break

    // Saves a category from this slot: NEW_NAME, ICON and/or COLOR.
    case 'saveCategory':
        var currentCategory = findCategory()
        var categoryChanges = {}
        if (arg('NEW_NAME') !== undefined) categoryChanges.name = arg('NEW_NAME')
        if (arg('ICON') !== undefined) categoryChanges.icon = arg('ICON')
        if (arg('COLOR') !== undefined) categoryChanges.color = arg('COLOR')
        output.category = putCategory(currentCategory, categoryChanges)
        break

    // Archives a category from this slot.
    case 'archiveCategory':
        var toArchiveCategory = findCategory()
        output.category = expect(call('POST', 'categories/' + toArchiveCategory.id + '/archive', { version: toArchiveCategory.version }, token()), 200, 'archiveCategory').body.data
        break

    // Deletes a category from this slot (JSON body on DELETE, backend spec 011). EXPECT is the status (200).
    case 'deleteCategory':
        var toDeleteCategory = findCategory()
        expect(call('DELETE_WITH_BODY', 'categories/' + toDeleteCategory.id, { version: toDeleteCategory.version }, token()), parseInt(arg('EXPECT', '200')), 'deleteCategory')
        break

    // Copies the default set into output.platformCategories, with per-kind counts in output.expenseDefaults / output.incomeDefaults
    // and the first names in output.firstExpenseName / output.firstIncomeName.
    case 'listPlatformCategories':
        var defaults = expect(call('GET', 'platform-categories', undefined, token()), 200, 'listPlatformCategories').body.data
        output.platformCategories = defaults
        output.expenseDefaults = defaults.filter(function (c) { return c.kind === 'expense' && c.archived_at === null }).length
        output.incomeDefaults = defaults.filter(function (c) { return c.kind === 'income' && c.archived_at === null }).length
        output.firstExpenseName = defaults.filter(function (c) { return c.kind === 'expense' })[0].names.en
        output.firstIncomeName = defaults.filter(function (c) { return c.kind === 'income' })[0].names.en
        break

    // Records an expense from this slot that uses a category, making it "in use" (backend B6):
    // CATEGORY (name) or CATEGORY_ID, WALLET_ID (default output.walletId from a createWallet), AMOUNT in minor units (1000).
    case 'createExpense':
        var usedCategory = findCategory()
        var walletId = arg('WALLET_ID', output.walletId)
        if (!walletId) throw new Error('createExpense: no WALLET_ID and no wallet created by this script run; run createWallet first')
        var wallet = expect(call('GET', 'wallets/' + walletId, undefined, token()), 200, 'getWallet').body.data
        expect(call('PUT', 'wallets/' + walletId + '/transactions/' + uuid7(), {
            kind: 'expense',
            date: dateArg(),
            amount_minor: parseInt(arg('AMOUNT', '1000')),
            currency: wallet.currency,
            category_id: usedCategory.id,
            counter_wallet_id: null,
            description: null,
            memo: null,
            counterparty: null,
            cleared: true,
            version: 0,
        }, token()), 201, 'createExpense')
        break

    // Copies a wallet's transactions into output.transactions and their number into output.transactionCount.
    // The wallet is WALLET_ID or WALLET; a transfer is listed in both of its wallets.
    case 'listTransactions':
        output.transactions = listAllTransactions(transactionWalletId())
        output.transactionCount = output.transactions.length
        break

    // Creates a transaction from this slot: KIND (expense), AMOUNT in minor units (1000), DATE (today), CATEGORY (name) or
    // CATEGORY_ID for income and expense, COUNTER_WALLET_ID for a transfer, NOTE, MEMO, PERSON. Copies it into output.transaction
    // and its id into output.transactionId.
    case 'createTransaction':
        var txWalletId = transactionWalletId()
        var txWallet = expect(call('GET', 'wallets/' + txWalletId, undefined, token()), 200, 'getWallet').body.data
        var txKind = arg('KIND', 'expense')
        var txId = uuid7()
        var createdTx = expect(call('PUT', 'wallets/' + txWalletId + '/transactions/' + txId, {
            kind: txKind,
            date: dateArg(),
            amount_minor: parseInt(arg('AMOUNT', '1000')),
            currency: txWallet.currency,
            category_id: txKind === 'transfer' ? null : resolveCategoryId(),
            counter_wallet_id: txKind === 'transfer' ? arg('COUNTER_WALLET_ID') : null,
            description: arg('NOTE', null),
            memo: arg('MEMO', null),
            counterparty: arg('PERSON', null),
            cleared: false,
            version: 0,
        }, token()), 201, 'createTransaction').body.data
        output.transaction = createdTx
        output.transactionId = createdTx.id
        break

    // Saves a transaction from this slot (the whole record, as the app does): ID or NOTE finds it; NEW_AMOUNT, NEW_NOTE, NEW_DATE change it.
    case 'saveTransaction':
        var savedWalletId = transactionWalletId()
        var currentTx = findTransaction(savedWalletId)
        var txChanges = {}
        if (arg('NEW_AMOUNT') !== undefined) txChanges.amount = parseInt(arg('NEW_AMOUNT'))
        if (arg('NEW_NOTE') !== undefined) txChanges.note = arg('NEW_NOTE')
        if (arg('NEW_DATE') !== undefined) txChanges.date = arg('NEW_DATE')
        output.transaction = expect(call('PUT', 'wallets/' + savedWalletId + '/transactions/' + currentTx.id, transactionBody(currentTx, txChanges), token()), 200, 'saveTransaction').body.data
        break

    // Deletes a transaction from this slot (JSON body on DELETE, backend spec 012). ID or NOTE finds it.
    case 'deleteTransaction':
        var deletedWalletId = transactionWalletId()
        var toDeleteTx = findTransaction(deletedWalletId)
        output.transaction = expect(call('DELETE_WITH_BODY', 'wallets/' + deletedWalletId + '/transactions/' + toDeleteTx.id, { version: toDeleteTx.version }, token()), 200, 'deleteTransaction').body.data
        break

    // Restores a deleted transaction from this slot (PATCH {"deleted_at": null, "version": n}); ID is required (a tombstone is not listed).
    case 'restoreTransaction':
        var restoredWalletId = transactionWalletId()
        var tombstone = expect(call('GET', 'wallets/' + restoredWalletId + '/transactions/' + arg('ID'), undefined, token()), 200, 'getTransaction').body.data
        output.transaction = expect(call('PATCH', 'wallets/' + restoredWalletId + '/transactions/' + arg('ID'), { deleted_at: null, version: tombstone.version }, token()), 200, 'restoreTransaction').body.data
        break

    // Creates a recurring rule from this slot: WALLET_ID/WALLET, DESCRIPTION, AMOUNT in minor units (450000), KIND (expense), CATEGORY
    // (name) or CATEGORY_ID, FREQUENCY (monthly), INTERVAL (1), ANCHOR_DAY (the start's day), START (today) or START_IN_DAYS (days from today), END or COUNT, AUTO_CONFIRM
    // ("true"), PRESET (custom), BASIS (per_occurrence), PAYOUT_ROUND, PAYOUT_CATEGORY_ID. Copies it into output.rule and its id
    // into output.ruleId. The server makes the occurrences; a rule starting today has one due at once.
    case 'createRule':
        var ruleWalletId = transactionWalletId()
        var ruleWallet = expect(call('GET', 'wallets/' + ruleWalletId, undefined, token()), 200, 'getWallet').body.data
        var newRuleId = uuid7()
        output.rule = expect(call('PUT', 'wallets/' + ruleWalletId + '/recurring-rules/' + newRuleId, ruleBody(ruleWallet, 0), token()), 201, 'createRule').body.data
        output.ruleId = newRuleId
        break

    // Deletes a rule from this slot (JSON body on DELETE): RULE_ID or the rule of this run.
    case 'deleteRule':
        var deleteRuleWalletId = transactionWalletId()
        var ruleToDelete = expect(call('GET', 'wallets/' + deleteRuleWalletId + '/recurring-rules/' + ruleId(), undefined, token()), 200, 'getRule').body.data
        output.rule = expect(call('DELETE_WITH_BODY', 'wallets/' + deleteRuleWalletId + '/recurring-rules/' + ruleId(), { version: ruleToDelete.version }, token()), 200, 'deleteRule').body.data
        break

    // Asks the server to make the rule's occurrences up to today ("Check for new dates"): RULE_ID or the rule of this run.
    // An auto-confirm rule is confirmed by the server here. Accepts 200 and 409 (nothing to make).
    case 'materialise':
        var materialiseResponse = call('POST', 'wallets/' + transactionWalletId() + '/recurring-rules/' + ruleId() + '/materialise', undefined, token())
        if (materialiseResponse.status !== 200 && materialiseResponse.status !== 409) {
            throw new Error('materialise: expected HTTP 200 or 409, got ' + materialiseResponse.status + ' ' + JSON.stringify(materialiseResponse.body))
        }
        break

    // Copies a wallet's occurrences (of RULE_ID, or of the rule of this run when there is one) into output.occurrences, their number
    // into output.occurrenceCount and the number of confirmed ones into output.confirmedCount.
    case 'listOccurrences':
        output.occurrences = listAllOccurrences(transactionWalletId())
        output.occurrenceCount = output.occurrences.length
        output.confirmedCount = output.occurrences.filter(function (o) { return o.status === 'confirmed' }).length
        break

    // Confirms the first scheduled occurrence of the rule from this slot, as another device would: AMOUNT (the planned one) in minor
    // units, DATE (the planned one). Copies the occurrence into output.occurrence and the transaction id into output.transactionId.
    case 'confirmOccurrence':
        var confirmWalletId = transactionWalletId()
        var scheduled = listAllOccurrences(confirmWalletId).filter(function (o) { return o.status === 'scheduled' })
        if (scheduled.length < 1) throw new Error('confirmOccurrence: no scheduled occurrence')
        var toConfirm = scheduled[0]
        var changes = { status: 'confirmed', transaction_id: uuid7() }
        if (arg('AMOUNT')) changes.amount_minor = parseInt(arg('AMOUNT'))
        if (arg('DATE')) changes.date = arg('DATE')
        output.occurrence = expect(call('PUT', 'wallets/' + confirmWalletId + '/occurrences/' + toConfirm.id, occurrenceBody(toConfirm, changes), token()), 200, 'confirmOccurrence').body.data
        output.transactionId = changes.transaction_id
        break

    // Budgets (spec 012). Creates a budget from this slot: CATEGORY (a name, own or default) or CATEGORY_ID; neither (or CATEGORY="")
    // means all expenses. AMOUNT is the limit in minor units (600000), CURRENCY (EGP). Copies it into output.budget and its id
    // into output.budgetId.
    case 'createBudget':
        var newBudgetId = uuid7()
        output.budget = expect(call('PUT', 'budgets/' + newBudgetId, {
            category_id: resolveCategoryId(),
            period_type: 'monthly',
            amount_minor: parseInt(arg('AMOUNT', '600000')),
            currency: arg('CURRENCY', 'EGP'),
            version: 0,
        }, token()), 201, 'createBudget').body.data
        output.budgetId = newBudgetId
        break

    // Saves a budget's limit from this slot (the whole record, as the app does): ID or output.budgetId, NEW_AMOUNT in minor units.
    case 'saveBudget':
        var currentBudget = findBudget()
        output.budget = expect(call('PUT', 'budgets/' + currentBudget.id, {
            category_id: currentBudget.category_id,
            period_type: currentBudget.period_type,
            amount_minor: parseInt(arg('NEW_AMOUNT', String(currentBudget.amount_minor))),
            currency: currentBudget.currency,
            version: currentBudget.version,
        }, token()), 200, 'saveBudget').body.data
        break

    // Archives a budget from this slot: ID or output.budgetId. Archiving is one-way.
    case 'archiveBudget':
        var toArchiveBudget = findBudget()
        output.budget = expect(call('POST', 'budgets/' + toArchiveBudget.id + '/archive', { version: toArchiveBudget.version }, token()), 200, 'archiveBudget').body.data
        break

    // Deletes a budget from this slot (JSON body on DELETE): ID or output.budgetId.
    case 'deleteBudget':
        var toDeleteBudget = findBudget()
        output.budget = expect(call('DELETE_WITH_BODY', 'budgets/' + toDeleteBudget.id, { version: toDeleteBudget.version }, token()), 200, 'deleteBudget').body.data
        break

    // Copies the caller's budgets into output.budgets and their number into output.budgetCount.
    case 'listBudgets':
        output.budgets = listAllBudgets()
        output.budgetCount = output.budgets.length
        break

    // Copies the progress of the active budgets (today's period) into output.progress and their number into output.progressCount.
    // With ID (or output.budgetId from a createBudget in this run) it also copies that budget's figures into output.spentMinor,
    // output.limitMinor, output.remainingMinor, output.projectedMinor (-1 when null), output.overLimit and output.otherCurrencies.
    case 'listBudgetProgress':
        output.progress = expect(call('GET', 'budget-progress?limit=200', undefined, token()), 200, 'listBudgetProgress').body.data
        output.progressCount = output.progress.length
        var progressId = arg('ID', output.budgetId)
        if (progressId) {
            var one = output.progress.filter(function (p) { return p.budget_id === progressId })
            if (one.length !== 1) throw new Error('listBudgetProgress: expected progress for ' + progressId + ', found ' + one.length)
            output.spentMinor = one[0].spent.amount_minor
            output.limitMinor = one[0].limit.amount_minor
            output.remainingMinor = one[0].remaining.amount_minor
            output.projectedMinor = one[0].projected_end === null ? -1 : one[0].projected_end.amount_minor
            output.overLimit = one[0].over_limit
            output.otherCurrencies = one[0].other_currencies.join(',')
            output.periodStart = one[0].period.start
            output.periodEnd = one[0].period.end
        }
        break

    // Sets the budget start day (1 to 28) from this slot: DAY. Copies the answer into output.profile; the date the new day takes over
    // is output.startDayFrom (null when it applies at once).
    case 'setStartDay':
        var profileNow = expect(call('GET', 'me', undefined, token()), 200, 'me').body.data.profile
        output.profile = expect(call('PUT', 'me/profile', {
            display_name: profileNow.display_name,
            locale: profileNow.locale,
            timezone: profileNow.timezone,
            default_currency: profileNow.default_currency,
            budget_period_start_day: parseInt(arg('DAY', '1')),
            version: profileNow.version,
        }, token()), 200, 'setStartDay').body.data
        output.startDayFrom = output.profile.budget_period_start_day_from
        break

    // Sharing (spec 013). Creates an invitation on WALLET_ID / WALLET / output.walletId from this slot (the owner): ROLE (viewer|editor,
    // default viewer), INVITEE_EMAIL (omit for a link invitation). Copies it into output.invitation and its id into output.invitationId;
    // for a link invitation the one-time link (meta.share_url) goes into output.shareUrl and the token into output.inviteToken.
    case 'createInvitation':
        var inviteWallet = transactionWalletId()
        var inviteId = uuid7()
        var inviteeEmail = arg('INVITEE_EMAIL')
        var createdInvite = expect(call('PUT', 'wallets/' + inviteWallet + '/invitations/' + inviteId, {
            role: arg('ROLE', 'viewer'),
            invitee_email: inviteeEmail ? inviteeEmail : null,
            version: 0,
        }, token()), 201, 'createInvitation')
        output.invitation = createdInvite.body.data
        output.invitationId = createdInvite.body.data.id
        output.shareUrl = createdInvite.body.meta && createdInvite.body.meta.share_url ? createdInvite.body.meta.share_url : ''
        output.inviteToken = output.shareUrl ? output.shareUrl.split('token=')[1] : ''
        break

    // Previews an invitation from this slot (the invitee): TOKEN or output.inviteToken; EXPECT (default 200). Copies the preview into output.preview.
    case 'previewInvitation':
        var previewed = expect(call('POST', 'invitations/preview', { token: arg('TOKEN', output.inviteToken) }, token()), parseInt(arg('EXPECT', '200')), 'previewInvitation')
        output.preview = previewed.body ? previewed.body.data : null
        break

    // Accepts an invitation from this slot (the invitee): TOKEN or output.inviteToken. Copies the member into output.member and the wallet id
    // into output.walletId.
    case 'acceptInvitation':
        output.member = expect(call('POST', 'invitations/accept', { token: arg('TOKEN', output.inviteToken) }, token()), 200, 'acceptInvitation').body.data
        output.walletId = output.member.wallet_id
        output.memberUserId = output.member.member_user_id
        break

    // Declines an invitation from this slot (the invitee): TOKEN or output.inviteToken.
    case 'declineInvitation':
        expect(call('POST', 'invitations/decline', { token: arg('TOKEN', output.inviteToken) }, token()), 204, 'declineInvitation')
        break

    // Changes a member's role from this slot (the owner): USER_ID (default output.memberUserId from an acceptInvitation), ROLE. The member is found in the wallet's member list for its version.
    case 'changeMemberRole':
        var roleMember = findMember()
        output.member = expect(call('PATCH', 'wallets/' + transactionWalletId() + '/members/' + roleMember.member_user_id, { role: arg('ROLE', 'viewer'), version: roleMember.version }, token()), 200, 'changeMemberRole').body.data
        break

    // Removes a member from this slot (the owner): USER_ID.
    case 'removeMember':
        var removed = findMember()
        output.member = expect(call('DELETE_WITH_BODY', 'wallets/' + transactionWalletId() + '/members/' + removed.member_user_id, { version: removed.version }, token()), 200, 'removeMember').body.data
        break

    // Copies the wallet's members into output.members and their number into output.memberCount.
    case 'listMembers':
        output.members = listAllMembers()
        output.memberCount = output.members.length
        break

    // Reports (spec 014). Moves money between two wallets of this slot: WALLET (source name) or WALLET_ID, COUNTER_WALLET (destination name)
    // or COUNTER_WALLET_ID, AMOUNT in minor units (1000), DATE (today), NOTE. Copies it into output.transaction.
    case 'createTransfer':
        var transferSourceId = transactionWalletId()
        var transferTargetId = arg('COUNTER_WALLET_ID') ? arg('COUNTER_WALLET_ID') : walletIdByName(arg('COUNTER_WALLET'))
        var transferSource = expect(call('GET', 'wallets/' + transferSourceId, undefined, token()), 200, 'getWallet').body.data
        var transferId = uuid7()
        output.transaction = expect(call('PUT', 'wallets/' + transferSourceId + '/transactions/' + transferId, {
            kind: 'transfer',
            date: dateArg(),
            amount_minor: parseInt(arg('AMOUNT', '1000')),
            currency: transferSource.currency,
            category_id: null,
            counter_wallet_id: transferTargetId,
            description: arg('NOTE', null),
            memo: null,
            counterparty: null,
            cleared: false,
            version: 0,
        }, token()), 201, 'createTransfer').body.data
        output.transactionId = transferId
        break

    // Reads the server's cross-wallet summary for the current Budget Period (CURRENCY, default EGP; WALLET_ID for one wallet's) into
    // output.reportSummary and its members as output.incomeMinor, output.expensesMinor, output.netMinor and output.balanceEndMinor.
    case 'getReportSummary':
        var reportPath = arg('WALLET_ID') ? 'wallets/' + arg('WALLET_ID') + '/reports/summary' : 'reports/summary?currency=' + arg('CURRENCY', 'EGP')
        var reportData = expect(call('GET', reportPath, undefined, token()), 200, 'getReportSummary').body.data
        output.reportSummary = reportData
        output.incomeMinor = reportData.income.amount_minor
        output.expensesMinor = reportData.expenses.amount_minor
        output.netMinor = reportData.net.amount_minor
        output.balanceEndMinor = reportData.balance_end.amount_minor
        break

    // Seeds the reports quickstart data set from this slot: wallets Cash (EGP, 1,000.00), Bank (EGP, 5,000.00) and Savings (USD, 900.00); a salary of
    // 10,000.00 into Bank, groceries 3,500.00 and transport 1,200.00 out of Cash, an own-category expense of 800.00 out of Bank (so income 10,000.00,
    // expenses 5,500.00, shares 64% / 22% / 15%), and a transfer of 500.00 from Cash to Bank (nowhere in a cross-wallet EGP report). Copies the ids into
    // output.cashId, output.bankId and output.savingsId. Dates are today's.
    case 'seedReportsData':
        seedReportsData()
        break

    default:
        throw new Error('api.js: unknown ACTION ' + arg('ACTION'))
}

// Reports (spec 014).
function walletIdByName(name) {
    var matches = listAllWallets().filter(function (w) { return w.name === name })
    if (matches.length !== 1) throw new Error('walletIdByName: expected one wallet named "' + name + '", found ' + matches.length)
    return matches[0].id
}

function putTransaction(walletId, currency, kind, amount, categoryId, counterWalletId, note) {
    return expect(call('PUT', 'wallets/' + walletId + '/transactions/' + uuid7(), {
        kind: kind,
        date: dateArg(),
        amount_minor: amount,
        currency: currency,
        category_id: categoryId,
        counter_wallet_id: counterWalletId,
        description: note,
        memo: null,
        counterparty: null,
        cleared: false,
        version: 0,
    }, token()), 201, 'seedReportsData ' + note).body.data
}

function defaultCategoryId(name) {
    var found = expect(call('GET', 'platform-categories', undefined, token()), 200, 'listPlatformCategories').body.data.filter(function (c) { return c.names.en === name })
    if (found.length < 1) throw new Error('defaultCategoryId: no platform category named "' + name + '"')
    return found[0].id
}

function seedReportsData() {
    function wallet(name, currency, opening) {
        return expect(call('PUT', 'wallets/' + uuid7(), { type: 'cash', name: name, currency: currency, opening_balance_minor: opening, icon: null, color: null, version: 0 }, token()), 201, 'seed wallet ' + name).body.data
    }
    var cash = wallet('Cash', 'EGP', 100000)
    var bank = wallet('Bank', 'EGP', 500000)
    var savings = wallet('Savings', 'USD', 90000)
    var pets = expect(call('PUT', 'categories/' + uuid7(), { kind: 'expense', name: 'Pets', icon: null, color: null, version: 0 }, token()), 201, 'seed category Pets').body.data
    putTransaction(bank.id, 'EGP', 'income', 1000000, defaultCategoryId('Salary'), null, 'Salary')
    putTransaction(cash.id, 'EGP', 'expense', 350000, defaultCategoryId('Groceries'), null, 'Weekly shop')
    putTransaction(cash.id, 'EGP', 'expense', 120000, defaultCategoryId('Transport'), null, 'Taxi')
    putTransaction(bank.id, 'EGP', 'expense', 80000, pets.id, null, 'Vet')
    putTransaction(cash.id, 'EGP', 'transfer', 50000, null, bank.id, 'Top up bank')
    output.cashId = cash.id
    output.bankId = bank.id
    output.savingsId = savings.id
    output.walletId = cash.id
}

// Budgets (spec 012). A budget is found by ID, or output.budgetId from a createBudget in this run.
function listAllBudgets() {
    return expect(call('GET', 'budgets?limit=200', undefined, token()), 200, 'listBudgets').body.data
}

function findBudget() {
    var id = arg('ID', output.budgetId)
    if (!id) throw new Error('findBudget: no ID and no budget created by this script run')
    var matches = listAllBudgets().filter(function (b) { return b.id === id })
    if (matches.length !== 1) throw new Error('findBudget: no budget ' + id)
    return matches[0]
}

// Sharing (spec 013). A member is found by USER_ID among the wallet's members.
function listAllMembers() {
    return expect(call('GET', 'wallets/' + transactionWalletId() + '/members?limit=50', undefined, token()), 200, 'listMembers').body.data
}

function findMember() {
    var userId = arg('USER_ID', output.memberUserId)
    var matches = listAllMembers().filter(function (m) { return m.member_user_id === userId })
    if (matches.length !== 1) throw new Error('findMember: no member ' + userId)
    return matches[0]
}
