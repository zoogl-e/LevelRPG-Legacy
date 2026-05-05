import os
import json
import glob

def refactor_json_trees():
    search_path = r"c:\Users\Zayne-PC\Desktop\Minecraft Modding\_IDEA (2025)\LevelRPG\src\main\resources\data\levelrpg\skill_trees\*.json"
    files = glob.glob(search_path)
    
    for file_path in files:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # Replace keys
        content = content.replace('"minSkillLevel"', '"minRank"')
        content = content.replace('"requiredSkillLevel"', '"requiredRank"')
        
        # Replace types
        content = content.replace('"type": "mastery"', '"type": "manifestation"')
        content = content.replace('"type": "keystone"', '"type": "axiom"')
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
            
        print(f"Refactored JSON: {os.path.basename(file_path)}")

def refactor_java_code():
    src_path = r"c:\Users\Zayne-PC\Desktop\Minecraft Modding\_IDEA (2025)\LevelRPG\src\main\java"
    
    replacements = [
        ("NodeType.KEYSTONE", "NodeType.AXIOM"),
        ("NodeType.MASTERY", "NodeType.MANIFESTATION"),
        ("requiredSkillLevel", "requiredRank"),
        ("skillLevel", "rank"),
        ("minSkillLevel", "minRank"),
        ("practiceXp", "potential"),
        ("availablePoints", "insight")
    ]
    
    # Files to be careful with or specific renames
    for root, _, files in os.walk(src_path):
        for file in files:
            if file.endswith('.java'):
                file_path = os.path.join(root, file)
                
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                original_content = content
                for old, new in replacements:
                    # We do a direct string replace for now, as these are very specific variable names
                    # In a real compiler-aware refactor we'd use AST, but this is safe for these specific names
                    # as long as we respect boundaries where necessary.
                    content = content.replace(old, new)
                    
                # Fixes for capitalization if we changed something like getSkillLevel -> getRank
                content = content.replace("getskillLevel", "getRank")
                content = content.replace("getSkillLevel", "getRank")
                content = content.replace("setSkillLevel", "setRank")
                content = content.replace("RequiredSkillLevel", "RequiredRank")
                content = content.replace("AvailablePoints", "Insight")
                content = content.replace("PracticeXp", "Potential")
                
                if content != original_content:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(content)
                    print(f"Refactored Java: {file}")

if __name__ == "__main__":
    refactor_json_trees()
    refactor_java_code()
    print("Done!")
