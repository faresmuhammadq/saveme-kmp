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
            date: arg('DATE', '2026-10-02'),
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

    default:
        throw new Error('api.js: unknown ACTION ' + arg('ACTION'))
}
