UPDATE users SET verification_token = sha256_hex(verification_token);
