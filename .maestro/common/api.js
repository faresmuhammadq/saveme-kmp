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

    default:
        throw new Error('api.js: unknown ACTION ' + arg('ACTION'))
}
