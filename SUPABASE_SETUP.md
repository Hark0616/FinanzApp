# Supabase setup for FinanzApp

This app expects a Supabase project with Google Auth enabled and the tables from `supabase_schema.sql`.

## 1. Create the database schema

If this is a brand-new Supabase project with no FinanzApp tables:

1. Open Supabase Dashboard.
2. Go to **SQL Editor**.
3. Paste and run the full contents of `supabase_schema.sql`.
4. Confirm that these tables exist: `accounts`, `credit_cards`, `categories`, `transactions`, `merchant_category_mappings`, `loans`, `loan_payments`, `deferred_purchases`, `assets`, `custom_rules`, `rule_contributions`, and `notification_sync_ledger`.

If you already ran an older FinanzApp schema, run `supabase_migration_from_old_schema.sql` instead. It patches existing tables with the columns needed by the current app.

The script enables Row Level Security so each authenticated user can only manage their own data. Default categories are readable by all authenticated users.

`rule_contributions` is the unified review inbox for custom rules that already processed at least one real notification correctly. App users can only contribute their own validated rules; project admins can review all rows from the Supabase dashboard or SQL editor.

## 2. Enable Google Auth

1. In Supabase, go to **Authentication > Providers > Google**.
2. Enable Google.
3. Paste the Google OAuth Web Client ID used by the Android app.
4. Paste the Google OAuth Client Secret in Supabase only.
5. Save.

Do not commit the client secret or place it in Android resources. The Android app only needs the Web Client ID.

## 3. Configure Google OAuth callback

In Google Cloud Console, add Supabase's callback URL to the Web OAuth client:

```text
https://ssxahbspsnogcuwafpjo.supabase.co/auth/v1/callback
```

The Android app uses this public Web Client ID:

```text
1000675040018-npmikovplk9g0sc5s3s2ulnpu6jgkks0.apps.googleusercontent.com
```

## 4. Create the Android OAuth client

Credential Manager also needs an Android OAuth client in the same Google Cloud project. This client does not have a secret and should not be pasted into Supabase or Android resources.

In Google Cloud Console, go to **APIs & Services > Credentials > Create credentials > OAuth client ID** and create a client with:

```text
Application type: Android
Package name: com.ivan.finanzapp
SHA-1 certificate fingerprint: 17:68:9D:E8:03:F7:72:EC:BA:8E:6E:5A:FC:AC:8E:E6:3D:4B:E1:4C
```

That SHA-1 is for the local debug APK signed with:

```text
C:\Users\H\.android\debug.keystore
```

If the OAuth app is still in testing mode, add the Google accounts used for testing under **Google Auth Platform > Audience > Test users**.

The Android app still uses the Web Client ID in `strings.xml`. The Android client simply allows Google to trust APKs signed with this debug key.

## 5. App project values

The Supabase project URL and publishable key currently live in:

```text
app/src/main/java/com/ivan/finanzapp/di/NetworkModule.kt
```

The Google Web Client ID lives in:

```text
app/src/main/res/values/strings.xml
```
