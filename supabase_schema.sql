-- ==========================================
-- FINANZAPP SUPABASE DATABASE SCHEMA
-- Execute this script in your Supabase SQL Editor.
-- ==========================================

-- Enable UUID extension if not enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    color TEXT NOT NULL,
    "budgetLimit" DOUBLE PRECISION,
    "isDefault" BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NULL
);

-- Enable RLS for categories
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;

-- 2. ACCOUNTS TABLE
CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    "currentBalance" DOUBLE PRECISION NOT NULL,
    "isManualBalance" BOOLEAN NOT NULL DEFAULT TRUE,
    "lastFourDigits" TEXT NULL,
    "createdAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for accounts
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;

-- 3. CREDIT CARDS TABLE
CREATE TABLE IF NOT EXISTS credit_cards (
    id TEXT PRIMARY KEY,
    "accountId" TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    "creditLimit" DOUBLE PRECISION NOT NULL,
    "currentDebt" DOUBLE PRECISION NOT NULL,
    "cutoffDay" INTEGER NOT NULL,
    "paymentDueDay" INTEGER NOT NULL,
    "minPaymentPercentage" DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    "minPaymentFloor" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestRateEA" DOUBLE PRECISION NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for credit cards
ALTER TABLE credit_cards ENABLE ROW LEVEL SECURITY;

-- 4. TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    "accountId" TEXT REFERENCES accounts(id) ON DELETE SET NULL,
    amount DOUBLE PRECISION NOT NULL,
    type TEXT NOT NULL,
    merchant TEXT NULL,
    "categoryId" TEXT REFERENCES categories(id) ON DELETE SET NULL,
    "rawNotification" TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    "confirmedByAI" BOOLEAN NOT NULL DEFAULT FALSE,
    "needsReview" BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for transactions
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

-- 5. MERCHANT CATEGORY MAPPINGS TABLE
CREATE TABLE IF NOT EXISTS merchant_category_mappings (
    "merchantKey" TEXT NOT NULL,
    "categoryId" TEXT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    "updatedAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid(),
    PRIMARY KEY ("merchantKey", user_id)
);

-- Enable RLS for merchant mappings
ALTER TABLE merchant_category_mappings ENABLE ROW LEVEL SECURITY;

-- 6. LOANS TABLE
CREATE TABLE IF NOT EXISTS loans (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    "totalAmount" DOUBLE PRECISION NOT NULL,
    "remainingAmount" DOUBLE PRECISION NOT NULL,
    "monthlyInterestRate" DOUBLE PRECISION NOT NULL,
    "interestRateInputValue" DOUBLE PRECISION NOT NULL,
    "interestRateType" TEXT NOT NULL,
    "amortizationType" TEXT NOT NULL,
    "totalInstallments" INTEGER NOT NULL,
    "paidInstallments" INTEGER NOT NULL DEFAULT 0,
    "monthlyInstallmentAmount" DOUBLE PRECISION NOT NULL,
    "fixedPrincipalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "monthlyInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "monthlyFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "paymentDay" INTEGER NOT NULL,
    "nextPaymentDate" BIGINT NOT NULL,
    "linkedAccountId" TEXT REFERENCES accounts(id) ON DELETE SET NULL,
    "createdAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for loans
ALTER TABLE loans ENABLE ROW LEVEL SECURITY;

-- 7. LOAN PAYMENTS TABLE
CREATE TABLE IF NOT EXISTS loan_payments (
    id TEXT PRIMARY KEY,
    "loanId" TEXT NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    "transactionId" TEXT REFERENCES transactions(id) ON DELETE SET NULL,
    "installmentNumber" INTEGER NOT NULL,
    "scheduledPaymentAmount" DOUBLE PRECISION NOT NULL,
    "actualPaymentAmount" DOUBLE PRECISION NOT NULL,
    "scheduledInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "insurancePaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unpaidInsuranceAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "scheduledFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "feePaidAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unpaidFeeAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "interestAccruedAmount" DOUBLE PRECISION NOT NULL,
    "interestPaidAmount" DOUBLE PRECISION NOT NULL,
    "unpaidInterestAmount" DOUBLE PRECISION NOT NULL,
    "principalAmount" DOUBLE PRECISION NOT NULL,
    "extraPrincipalAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "unappliedPaymentAmount" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "remainingAmountBefore" DOUBLE PRECISION NOT NULL,
    "remainingAmountAfter" DOUBLE PRECISION NOT NULL,
    "paymentDate" BIGINT NOT NULL,
    "createdAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for loan payments
ALTER TABLE loan_payments ENABLE ROW LEVEL SECURITY;

-- 8. DEFERRED PURCHASES TABLE
CREATE TABLE IF NOT EXISTS deferred_purchases (
    id TEXT PRIMARY KEY,
    "creditCardId" TEXT NOT NULL REFERENCES credit_cards(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    "totalAmount" DOUBLE PRECISION NOT NULL,
    "totalInstallments" INTEGER NOT NULL,
    "paidInstallments" INTEGER NOT NULL,
    "purchaseDate" BIGINT NOT NULL,
    "interestRateEA" DOUBLE PRECISION NULL,
    "createdAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for deferred purchases
ALTER TABLE deferred_purchases ENABLE ROW LEVEL SECURITY;

-- 9. ASSETS TABLE
CREATE TABLE IF NOT EXISTS assets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    type TEXT NOT NULL,
    "createdAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for assets
ALTER TABLE assets ENABLE ROW LEVEL SECURITY;

-- 10. CUSTOM RULES TABLE
CREATE TABLE IF NOT EXISTS custom_rules (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    "regexPattern" TEXT NOT NULL,
    "transactionType" TEXT NOT NULL,
    "bankSource" TEXT NOT NULL,
    "amountFormatType" INTEGER NOT NULL,
    "createdAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for custom rules
ALTER TABLE custom_rules ENABLE ROW LEVEL SECURITY;

-- 11. NOTIFICATION SYNC LEDGER TABLE
CREATE TABLE IF NOT EXISTS notification_sync_ledger (
    id TEXT PRIMARY KEY,
    "packageName" TEXT NOT NULL,
    title TEXT NOT NULL,
    text TEXT NOT NULL,
    "postedAtMillis" BIGINT NOT NULL,
    "receivedAtMillis" BIGINT NOT NULL,
    status TEXT NOT NULL,
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
    "updatedAtMillis" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid()
);

-- Enable RLS for notification sync ledger
ALTER TABLE notification_sync_ledger ENABLE ROW LEVEL SECURITY;

-- 12. PAYMENT MATCH SUGGESTIONS TABLE
CREATE TABLE IF NOT EXISTS payment_match_suggestions (
    id TEXT PRIMARY KEY,
    "sourceTransactionId" TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    "targetType" TEXT NOT NULL,
    "targetId" TEXT NOT NULL,
    "targetName" TEXT NOT NULL,
    "expectedAmount" DOUBLE PRECISION NOT NULL,
    "actualAmount" DOUBLE PRECISION NOT NULL,
    "differenceAmount" DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL,
    "createdAt" BIGINT NOT NULL,
    "updatedAt" BIGINT NOT NULL,
    "expiresAt" BIGINT NULL,
    "acceptedApplicationId" TEXT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid(),
    UNIQUE ("sourceTransactionId", "targetType", "targetId", user_id)
);

-- Enable RLS for payment match suggestions
ALTER TABLE payment_match_suggestions ENABLE ROW LEVEL SECURITY;

-- 13. DEBT PAYMENT APPLICATIONS TABLE
CREATE TABLE IF NOT EXISTS debt_payment_applications (
    id TEXT PRIMARY KEY,
    "sourceTransactionId" TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    "suggestionId" TEXT NULL,
    "targetType" TEXT NOT NULL,
    "targetId" TEXT NOT NULL,
    "targetName" TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    "expectedAmount" DOUBLE PRECISION NOT NULL,
    "differenceAmount" DOUBLE PRECISION NOT NULL,
    "applicationType" TEXT NOT NULL,
    "appliedAt" BIGINT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL DEFAULT auth.uid(),
    UNIQUE ("sourceTransactionId", user_id)
);

-- Enable RLS for debt payment applications
ALTER TABLE debt_payment_applications ENABLE ROW LEVEL SECURITY;


-- ==========================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ==========================================

-- Keep this setup script re-runnable while the project is being configured.
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
DROP POLICY IF EXISTS "Manage own payment suggestions" ON payment_match_suggestions;
DROP POLICY IF EXISTS "Manage own debt payment applications" ON debt_payment_applications;

-- Policies for CATEGORIES (users can read defaults and their own, but only write their own)
CREATE POLICY "Read categories" ON categories FOR SELECT 
    USING (user_id IS NULL OR auth.uid() = user_id);
CREATE POLICY "Insert own categories" ON categories FOR INSERT 
    WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Update own categories" ON categories FOR UPDATE 
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Delete own categories" ON categories FOR DELETE 
    USING (auth.uid() = user_id);

-- Standard security policies for user-owned tables
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
CREATE POLICY "Manage own payment suggestions" ON payment_match_suggestions FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Manage own debt payment applications" ON debt_payment_applications FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);


-- ==========================================
-- DEFAULT SEED DATA
-- ==========================================

-- Insert initial categories matching DefaultCategories.kt
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
