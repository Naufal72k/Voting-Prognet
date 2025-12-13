import re
import os

files = [
    r"c:\Users\lenov\OneDrive\Desktop\Github\Voting-Prognet\VoterClient.java",
    r"c:\Users\lenov\OneDrive\Desktop\Github\Voting-Prognet\ServerAdmin.java"
]

# Regex to match comments and strings
# We match strings/chars to CONSUME them so we don't detect comments inside them.
# Group 1: Single line comment
# Group 2: Block comment
# Group 3: Strings/Chars (preserve these)
pattern = re.compile(
    r'(//[^\n]*)|(/\*[\s\S]*?\*/)|("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')'
)

def replacer(match):
    # If group 3 (string/char) matched, return it as is
    if match.group(3):
        return match.group(3)
    # Otherwise it's a comment, return empty string (or space/newline if needed to be safe)
    # For block comments, simply removing them might merge tokens: int/*..*/x -> intx
    # So replacing with a space is safer.
    # For single line comments, they end at newline. We should preserve the newline?
    # The regex `//[^\n]*` does not consume the newline. So replacing with empty string is fine, the newline remains.
    return " "

for file_path in files:
    print(f"Processing {file_path}...")
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        new_content = pattern.sub(replacer, content)
        
        # Cleanup: Remove trailing whitespace on lines and multiple empty lines?
        # The user only asked to remove comments.
        # But replacing //... with " " might leave trailing spaces.
        # Let's simple write it back.
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
            
        print(f"Cleaned {file_path}")
    except Exception as e:
        print(f"Error processing {file_path}: {e}")
