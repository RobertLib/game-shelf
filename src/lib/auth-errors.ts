import { isAuthError } from '@supabase/supabase-js';

/** Turns Supabase Auth errors into messages that make sense to a user. */
export function authErrorMessage(error: unknown) {
  if (isAuthError(error)) {
    switch (error.code) {
      case 'invalid_credentials':
        return 'Wrong email or password.';
      case 'email_not_confirmed':
        return 'Please confirm your email first. Check your inbox for the confirmation link.';
      case 'user_already_exists':
      case 'email_exists':
        return 'An account with this email already exists. Sign in instead.';
      case 'weak_password':
        return 'This password is too weak. Try a longer one with numbers or symbols.';
      case 'over_email_send_rate_limit':
      case 'over_request_rate_limit':
        return 'Too many attempts. Please wait a minute and try again.';
      case 'signup_disabled':
        return 'New sign-ups are currently disabled.';
    }
    if (error.status === 0 || error.name === 'AuthRetryableFetchError') {
      return 'Could not reach the server. Check your internet connection.';
    }
    return error.message;
  }
  return 'Something went wrong. Please try again.';
}
