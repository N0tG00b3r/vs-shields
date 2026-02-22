import os
filepath = 'common/src/main/resources/assets/vs_shields/models/block/iron_shield_generator_model.obj'
with open(filepath, 'r') as f:
    lines = f.readlines()

new_lines = []
cube_counter = 1
for line in lines:
    if line.startswith('o '):
        new_lines.append(f'o cube_{cube_counter}\n')
        cube_counter += 1
    elif line.startswith('g '):
        new_lines.append(f'g group_{cube_counter}\n')
        cube_counter += 1
    else:
        new_lines.append(line)

with open(filepath, 'w') as f:
    f.writelines(new_lines)

print(f'Successfully renamed {cube_counter - 1} objects/groups cleanly.')
