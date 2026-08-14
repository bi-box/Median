#!/usr/bin/env python3
"""Lightweight delimiter/string/comment sanity check; Android/Gradle compile remains authoritative."""
from pathlib import Path
import sys

PAIRS = {')': '(', ']': '[', '}': '{'}
OPEN = set(PAIRS.values())

def check(path: Path):
    text = path.read_text(encoding='utf-8')
    stack = []
    state = 'code'
    line = 1
    i = 0
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ''
        if c == '\n': line += 1
        if state == 'code':
            if c == '/' and n == '/': state = 'line'; i += 1
            elif c == '/' and n == '*': state = 'block'; i += 1
            elif c == '"': state = 'string'
            elif c == "'": state = 'char'
            elif c in OPEN: stack.append((c, line))
            elif c in PAIRS:
                if not stack or stack[-1][0] != PAIRS[c]:
                    raise ValueError(f'{path}:{line}: unmatched {c}')
                stack.pop()
        elif state == 'line':
            if c == '\n': state = 'code'
        elif state == 'block':
            if c == '*' and n == '/': state = 'code'; i += 1
        elif state in ('string', 'char'):
            if c == '\\': i += 1
            elif (state == 'string' and c == '"') or (state == 'char' and c == "'"):
                state = 'code'
            elif c == '\n':
                raise ValueError(f'{path}:{line}: newline inside {state}')
        i += 1
    if state not in ('code', 'line'):
        raise ValueError(f'{path}:{line}: unterminated {state}')
    if stack:
        c, opened = stack[-1]
        raise ValueError(f'{path}:{opened}: unclosed {c}')

errors = []
for file in sorted(Path('app/src').rglob('*.java')):
    try: check(file)
    except Exception as exc: errors.append(str(exc))
if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Java syntax sanity passed for', len(list(Path('app/src').rglob('*.java'))), 'files')
