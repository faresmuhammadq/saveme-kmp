# C5 · Categories (client)

**Pairs with**: backend [B5](../../../saveme-backend/specs/briefs/B05-categories.md) · **Call with**: `/speckit-specify specs/briefs/C05-categories.md`

## Description

Pick a category when recording a transaction, and manage the user's own categories (create,
rename, icon/colour, archive). Show the platform's default set in the user's language beside
their own. In a shared wallet, show a partner's category through the read-only category
reference the wallet feed carries.

## Consumes

The B5 endpoints (user-scoped; the brief's wallet-scoped paths are superseded by the owner
decision), the platform set with `system_key`, and the category reference on the wallet feed.

## Can build before the backend ships

Local schema for user categories and a cached platform set; the picker and management screens;
archive-not-delete behaviour; contract tests once the fragment exists.

## Needs from the backend

What "use" means for a platform category (reference vs copy into the user's set), how archiving a
platform category works, the `system_key` list, and the category reference shape on wallet feeds.
These are open in B5; the client spec cannot freeze before they are settled.

## Considerations

- Names switch with the app language for platform categories, never for the user's own.
- Archived categories stay selectable on existing transactions and hidden from the picker.
- No nesting in MVP (field reserved server-side).

## Integration checkpoint

E2E: record a transaction with a platform category and a custom one; archive the custom one; the
transaction still shows it.
