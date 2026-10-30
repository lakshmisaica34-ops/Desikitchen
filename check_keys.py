#!/usr/bin/env python3
"""Prints present/absent for scraper keys baked into the debug BuildConfig. Never prints values."""
import glob, re
bc = open(glob.glob('app/build/generated/source/buildConfig/debug/**/BuildConfig.java', recursive=True)[0]).read()
for k in ['CREATORS_API_URL', 'CREATORS_API_KEY', 'OPENROUTER_API_KEY']:
    m = re.search(k + r' = "(.*)";', bc)
    print(k, 'present' if m and len(m.group(1)) > 0 else 'absent')
