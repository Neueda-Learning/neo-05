const PRODUCT_DISPLAY_NAMES = {
  CREDIT_CARD_REWARDS: 'Rewards',
  REWARDS: 'Rewards',
  CREDIT_CARD_PREMIUM: 'Rewards',
  PREMIUM: 'Rewards',
  CREDIT_CARD_STANDARD: 'Standard',
  STANDARD: 'Standard',
  CREDIT_CARD_LOW_RATE: 'Standard',
  LOW_RATE: 'Standard',
  CREDIT_CARD_PLATINUM: 'Standard',
  PLATINUM: 'Standard',
  CREDIT_CARD_STUDENT: 'Student',
  STUDENT: 'Student',
};

export function displayProductName(value, fallback = '-') {
  if (value == null || String(value).trim() === '') {
    return fallback;
  }
  const normalized = String(value).trim().toUpperCase();
  return PRODUCT_DISPLAY_NAMES[normalized] || String(value);
}
