# ODTÜ CAS login ("ODTÜ ile giriş yap")

ODTÜ signs its people in through CAS. The provider for it (`CasLoginProvider`, id `odtu-cas`) is built and **off by
default**. Once ODTÜ answers the questions below, switching it on is configuration only. ODTÜ edition only.

## What we must ask ODTÜ for

1. **The CAS server address**, for example `https://…metu.edu.tr/cas`. It must be https.
2. **The protocol version: CAS 3.0 or 2.0.** 3.0 (`/p3/serviceValidate`) returns attributes. 2.0
   (`/serviceValidate`) by the standard returns only the user name, which is not enough for us (see 4).
3. **Registering Menton as a service.** Who does it, and how long does it take? Our service address is
   `https://<menton address>/login/odtu/callback`.
   - **Register the service as a prefix/pattern that allows `?state=`.** We add a one-time `state` value to the
     service address on every sign-in (`…/login/odtu/callback?state=…`), so an exact-match registration would refuse
     every login.
   - The service must be allowed to **receive attributes** (the "attribute release" setting on their side).
4. **Which attributes come back, and under which names.** We need:
   - **an id that never changes** for the person, and is never given to someone else later. Is the CAS user name
     ever reused (after a graduate leaves, say)? If it can be, we need a separate attribute (a student or staff number)
     for `CAS_ATTR_ID`: the id is what an account is bound to, so a reused user name would hand the old person's
     account to the new one;
   - **the e-mail address**, and whether it is always the person's own `@metu.edu.tr` address;
   - **the full name** (one attribute, or given name and surname apart?);
   - **the kind of person**: student, academic staff, administrative staff. Which attribute, and which exact
     values? What does a research assistant, who is both, look like?
   - optional: **department** and **faculty**.
5. **Test accounts:** one student, one academic, and if possible one research assistant and one administrative
   staff member.
6. **Sign-out:** does ODTÜ expect us to call CAS logout when someone signs out of Menton? (Not built; today signing
   out of Menton leaves the CAS session alone.)
7. **A sample answer** of the validation call for a test account, with the values blanked. It settles 2 and 4 at
   once.
8. **Who else can set cookies next to us.** If Menton runs on a `metu.edu.tr` subdomain, can other hosts under that
   domain (student or staff pages, anything users host) set cookies for the parent domain? If they can, the sign-in
   binding cookie must first move to a `__Host-` name (a separate change, shared with the OTG login) before CAS is
   switched on in production.

## Switching it on

Before production: answer 4 decides `CAS_ATTR_ID`, and answer 8 decides whether the cookie change must land first.

Set these on the backend (see `.env.example`), then restart:

| Variable | Value |
|---|---|
| `CAS_LOGIN_ENABLED` | `true` |
| `CAS_BASE_URL` | ODTÜ's CAS address (answer 1), https only |
| `CAS_SERVICE_URL` | `https://<menton address>/login/odtu/callback`, with no query. Plain http is taken for `localhost` only. |
| `CAS_VERSION` | `3.0` or `2.0` (answer 2) |
| `CAS_EMAIL_DOMAINS` | `metu.edu.tr` (subdomains count) |
| `CAS_ATTR_ID` | the id attribute; empty means the CAS user name. Set it whenever the user name can be reused (answer 4). |
| `CAS_ATTR_EMAIL`, `CAS_ATTR_NAME` | the e-mail and name attributes (defaults `mail`, `cn`) |
| `CAS_ATTR_KIND` | the attribute that says what the person is (default `eduPersonAffiliation`) |
| `CAS_KIND_STUDENT`, `CAS_KIND_ACADEMIC`, `CAS_KIND_STAFF` | the values of that attribute, comma-separated (defaults `student`, `faculty`, `staff,employee`) |
| `CAS_ATTR_DEPARTMENT`, `CAS_ATTR_FACULTY` | optional; empty leaves them out |

With `CAS_LOGIN_ENABLED=true`, a missing or non-https address, an unknown version or an empty e-mail attribute stops
the start-up. The frontend needs no setting: it asks the backend which logins are on and shows the button then.

If a sign-in fails with "ODTÜ ile giriş şu an tamamlanamadı", the backend log says which attribute was missing and
lists the **names** of the attributes CAS did send (never their values), so the `CAS_ATTR_*` settings can be put
right.

## How it works

1. The browser asks to start; the backend makes a single-use state (10 minutes) and sends the browser to
   `{CAS_BASE_URL}/login?service={CAS_SERVICE_URL}?state=…`.
2. CAS signs the person in and sends the browser back to the service address with a one-time `ticket`.
3. The backend asks CAS about the ticket (`/p3/serviceValidate` or `/serviceValidate`), with the service address it
   rebuilds itself from its configuration and the state. Nothing else from the browser is trusted.
4. The answer is mapped to a person, and `InstitutionLoginService` finds or creates the account and hands the
   browser a Firebase session, as for every institution login.

## The rules it keeps

- **The ticket is tied to the browser's own login.** CAS accepts a ticket only under the exact service address it
  was issued for, and ours carries the state. A ticket from someone else's login fails.
- **The XML answer is read with document types and outside references off** (no XXE), and anything that is not one
  CAS answer is refused.
- **The address must be under `CAS_EMAIL_DOMAINS`**, or the login is refused. That is what makes it safe to bind a
  CAS login to an account by its address.
- **One account per person.** A student who already signed up with their `@metu.edu.tr` address lands on that same
  account, and keeps the department and year they typed unless CAS sends its own. Admin and coordinator accounts are
  never bound to a CAS login; they keep their e-mail and password.
- **Who gets in.** A student becomes a mentee and joins the one running peer-mentoring program (with none or
  several, they get in without a program and the log says so). An academic becomes an academician. Anyone else is
  only identified: they go through Menton's ordinary sign-up.
- **CAS never blanks what it does not send.** A field CAS leaves out stays as an import or the sign-up filed it.
- **Academician lists and advisor pairs** that are imported for these people must be filed under the institution
  `odtu-cas`, the same key the login writes.
