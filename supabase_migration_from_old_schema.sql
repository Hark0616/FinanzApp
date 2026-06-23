-- ==========================================
-- FINANZAPP SUPABASE MIGRATION FROM OLD SCHEMA
-- Run this when an older FinanzApp schema already exists in Supabase.
-- Safe to run more than once.
-- ==========================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==========================================
-- TABLES THAT MAY NOT EXIST IN OLDER PROJECTS
-- ==========================================

CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    color TEXT NOT NULL,
    "budgetLimit" DOUBLE PRECISION,
    "isDefault" BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NULL
);

CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    "currentBalance" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "isManualBalance" BOOLEAN NOT NULL DEFAULT TRUE,
    "lastFourDigits" TEXT NULL,
    "createdAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS credit_cards (
    id TEXT PRIMARY KEY,
    "accountId" TEXT NOT NULL,
    "creditLimit" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "currentDebt" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "cutoffDay" INTEGER NOT NULL DEFAULT 1,
    "paymentDueDay" INTEGER NOT NULL DEFAULT 1,
    "minPaymentPercentage" DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    "minPaymentFloor" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestRateEA" DOUBLE PRECISION NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    "accountId" TEXT NULL,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    type TEXT NOT NULL,
    merchant TEXT NULL,
    "categoryId" TEXT NULL,
    "rawNotification" TEXT NOT NULL DEFAULT '',
    timestamp BIGINT NOT NULL DEFAULT 0,
    "confirmedByAI" BOOLEAN NOT NULL DEFAULT FALSE,
    "needsReview" BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS merchant_category_mappings (
    "merchantKey" TEXT NOT NULL,
    "categoryId" TEXT NOT NULL,
    "updatedAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid(),
    PRIMARY KEY ("merchantKey", user_id)
);

CREATE TABLE IF NOT EXISTS loans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    "totalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "remainingAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "monthlyInterestRate" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestRateInputValue" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestRateType" TEXT NOT NULL DEFAULT 'MONTHLY_EFFECTIVE',
    "amortizationType" TEXT NOT NULL DEFAULT 'FIXED_INSTALLMENT',
    "totalInstallments" INTEGER NOT NULL DEFAULT 1,
    "paidInstallments" INTEGER NOT NULL DEFAULT 0,
    "monthlyInstallmentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "fixedPrincipalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "monthlyInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "monthlyFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "paymentDay" INTEGER NOT NULL DEFAULT 1,
    "nextPaymentDate" BIGINT NOT NULL DEFAULT 0,
    "linkedAccountId" TEXT NULL,
    "createdAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS loan_payments (
    id TEXT PRIMARY KEY,
    "loanId" TEXT NOT NULL,
    "transactionId" TEXT NULL,
    "installmentNumber" INTEGER NOT NULL DEFAULT 1,
    "scheduledPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "actualPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "scheduledInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "insurancePaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unpaidInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "scheduledFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "feePaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unpaidFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestAccruedAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestPaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unpaidInterestAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "principalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "extraPrincipalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unappliedPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "remainingAmountBefore" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "remainingAmountAfter" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "paymentDate" BIGINT NOT NULL DEFAULT 0,
    "createdAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS deferred_purchases (
    id TEXT PRIMARY KEY,
    "creditCardId" TEXT NOT NULL,
    description TEXT NOT NULL,
    "totalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "totalInstallments" INTEGER NOT NULL DEFAULT 1,
    "paidInstallments" INTEGER NOT NULL DEFAULT 0,
    "purchaseDate" BIGINT NOT NULL DEFAULT 0,
    "interestRateEA" DOUBLE PRECISION NULL,
    "createdAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS assets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    type TEXT NOT NULL,
    "createdAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS custom_rules (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    "regexPattern" TEXT NOT NULL,
    "transactionType" TEXT NOT NULL,
    "bankSource" TEXT NOT NULL,
    "amountFormatType" INTEGER NOT NULL DEFAULT 0,
    "createdAt" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

CREATE TABLE IF NOT EXISTS notification_sync_ledger (
    id TEXT PRIMARY KEY,
    "packageName" TEXT NOT NULL,
    title TEXT NOT NULL,
    text TEXT NOT NULL,
    "postedAtMillis" BIGINT NOT NULL DEFAULT 0,
    "receivedAtMillis" BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'RECEIVED',
    "statusReason" TEXT NULL,
    "transactionId" TEXT NULL,
    "accountId" TEXT NULL,
    "categoryId" TEXT NULL,
    "transactionType" TEXT NULL,
    amount DOUBLE PRECISION NULL,
    merchant TEXT NULL,
    "bankSource" TEXT NULL,
    confidence DOUBLE PRECISION NULL,
    "classifierSource" TEXT NULL,
    "errorMessage" TEXT NULL,
    "processedAtMillis" BIGINT NULL,
    "updatedAtMillis" BIGINT NOT NULL DEFAULT 0,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- ==========================================
-- PATCH EXISTING TABLES
-- ==========================================

ALTER TABLE categories ADD COLUMN IF NOT EXISTS "budgetLimit" DOUBLE PRECISION;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS "isDefault" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NULL;

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS "currentBalance" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS "isManualBalance" BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS "lastFourDigits" TEXT NULL;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS "createdAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "accountId" TEXT;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "creditLimit" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "currentDebt" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "cutoffDay" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "paymentDueDay" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "minPaymentPercentage" DOUBLE PRECISION NOT NULL DEFAULT 5.0;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "minPaymentFloor" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS "interestRateEA" DOUBLE PRECISION NULL;
ALTER TABLE credit_cards ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS "accountId" TEXT NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS amount DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'GASTO';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS merchant TEXT NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS "categoryId" TEXT NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS "rawNotification" TEXT NOT NULL DEFAULT '';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS timestamp BIGINT NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS "confirmedByAI" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS "needsReview" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE merchant_category_mappings ADD COLUMN IF NOT EXISTS "merchantKey" TEXT;
ALTER TABLE merchant_category_mappings ADD COLUMN IF NOT EXISTS "categoryId" TEXT;
ALTER TABLE merchant_category_mappings ADD COLUMN IF NOT EXISTS "updatedAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE merchant_category_mappings ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE loans ADD COLUMN IF NOT EXISTS "monthlyInterestRate" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "interestRateInputValue" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "interestRateType" TEXT NOT NULL DEFAULT 'MONTHLY_EFFECTIVE';
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "amortizationType" TEXT NOT NULL DEFAULT 'FIXED_INSTALLMENT';
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "paidInstallments" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "monthlyInstallmentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "fixedPrincipalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "monthlyInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "monthlyFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "paymentDay" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "nextPaymentDate" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "linkedAccountId" TEXT NULL;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS "createdAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE loans ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "transactionId" TEXT NULL;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "installmentNumber" INTEGER NOT NULL DEFAULT 1;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "scheduledPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "actualPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "scheduledInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "insurancePaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "unpaidInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "scheduledFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "feePaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "unpaidFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "interestAccruedAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "interestPaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "unpaidInterestAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "principalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "extraPrincipalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "unappliedPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "remainingAmountBefore" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "remainingAmountAfter" DOUBLE PRECISION NOT NULL DEFAULT 0.0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "paymentDate" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS "createdAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE loan_payments ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE deferred_purchases ADD COLUMN IF NOT EXISTS "creditCardId" TEXT;
ALTER TABLE deferred_purchases ADD COLUMN IF NOT EXISTS "interestRateEA" DOUBLE PRECISION NULL;
ALTER TABLE deferred_purchases ADD COLUMN IF NOT EXISTS "createdAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE deferred_purchases ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE assets ADD COLUMN IF NOT EXISTS "createdAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE assets ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE custom_rules ADD COLUMN IF NOT EXISTS "createdAt" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE custom_rules ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "statusReason" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "transactionId" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "accountId" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "categoryId" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "transactionType" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS amount DOUBLE PRECISION NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS merchant TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "bankSource" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "classifierSource" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "errorMessage" TEXT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "processedAtMillis" BIGINT NULL;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS "updatedAtMillis" BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification_sync_ledger ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

-- ==========================================
-- RLS POLICIES
-- ==========================================

ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE credit_cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE merchant_category_mappings ENABLE ROW LEVEL SECURITY;
ALTER TABLE loans ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE deferred_purchases ENABLE ROW LEVEL SECURITY;
ALTER TABLE assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE custom_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_sync_ledger ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Read categories" ON categories;
DROP POLICY IF EXISTS "Insert own categories" ON categories;
DROP POLICY IF EXISTS "Update own categories" ON categories;
DROP POLICY IF EXISTS "Delete own categories" ON categories;
DROP POLICY IF EXISTS "Manage own accounts" ON accounts;
DROP POLICY IF EXISTS "Manage own credit cards" ON credit_cards;
DROP POLICY IF EXISTS "Manage own transactions" ON transactions;
DROP POLICY IF EXISTS "Manage own mappings" ON merchant_category_mappings;
DROP POLICY IF EXISTS "Manage own loans" ON loans;
DROP POLICY IF EXISTS "Manage own loan payments" ON loan_payments;
DROP POLICY IF EXISTS "Manage own deferred purchases" ON deferred_purchases;
DROP POLICY IF EXISTS "Manage own assets" ON assets;
DROP POLICY IF EXISTS "Manage own custom rules" ON custom_rules;
DROP POLICY IF EXISTS "Manage own ledger" ON notification_sync_ledger;

CREATE POLICY "Read categories" ON categories FOR SELECT
    USING (user_id IS NULL OR auth.uid() = user_id);
CREATE POLICY "Insert own categories" ON categories FOR INSERT
    WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Update own categories" ON categories FOR UPDATE
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Delete own categories" ON categories FOR DELETE
    USING (auth.uid() = user_id);

CREATE POLICY "Manage own accounts" ON accounts FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own credit cards" ON credit_cards FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own transactions" ON transactions FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own mappings" ON merchant_category_mappings FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own loans" ON loans FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own loan payments" ON loan_payments FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own deferred purchases" ON deferred_purchases FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own assets" ON assets FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own custom rules" ON custom_rules FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own ledger" ON notification_sync_ledger FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ==========================================
-- DEFAULT CATEGORIES
-- ==========================================

INSERT INTO categories (id, name, icon, color, "budgetLimit", "isDefault", user_id) VALUES
('cat_mercado', 'Mercado', 'ShoppingCart', '#4CAF50', NULL, TRUE, NULL),
('cat_transporte', 'Transporte', 'DirectionsCar', '#2196F3', NULL, TRUE, NULL),
('cat_restaurantes', 'Restaurantes', 'Restaurant', '#FF9800', NULL, TRUE, NULL),
('cat_suscripciones', 'Suscripciones', 'Subscriptions', '#9C27B0', NULL, TRUE, NULL),
('cat_salud', 'Salud', 'LocalHospital', '#F44336', NULL, TRUE, NULL),
('cat_servicios', 'Servicios públicos', 'Bolt', '#FFC107', NULL, TRUE, NULL),
('cat_hogar', 'Hogar', 'Home', '#795548', NULL, TRUE, NULL),
('cat_entretenimiento', 'Entretenimiento', 'Movie', '#E91E63', NULL, TRUE, NULL),
('cat_educacion', 'Educación', 'School', '#3F51B5', NULL, TRUE, NULL),
('cat_ingresos', 'Ingresos', 'TrendingUp', '#009688', NULL, TRUE, NULL),
('cat_pago_tc', 'Pago tarjeta de crédito', 'CreditCard', '#607D8B', NULL, TRUE, NULL),
('cat_otros', 'Otros', 'Category', '#9E9E9E', NULL, TRUE, NULL)
ON CONFLICT (id) DO NOTHING;
