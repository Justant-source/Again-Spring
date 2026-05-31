const { authenticator } = require('otplib');
// Configure to be more lenient with time drift
authenticator.options = { window: 1 };

function generateTotp(secret) {
  return authenticator.generate(secret);
}

module.exports = { generateTotp };
