import type { Config } from './api';

/**
 * The app connects to this engine on first launch, so there is nothing to set up.
 * Keep this repository PRIVATE: anyone who sees the key can read and edit the watchlist.
 * To lock out an old copy, change the key in the sheet's Settings tab (B2) and here.
 */
export const DEFAULT_CONFIG: Config = {
  url: 'https://script.google.com/macros/s/AKfycbwtSiaDgdsQyJHgC6ZAXv66XGfCFjEICVFBfsz12LIpc25ee_F4Na8EEC6QOLEHobKB/exec',
  key: 'RS5NYfswFosn7rseUuG_x5WqIdbvPeQ0',
};
