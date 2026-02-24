import os

obj_file = 'common/src/main/resources/assets/vs_shields/models/block/gravitational_mine_launcher_model.obj'
mtl_file = 'common/src/main/resources/assets/vs_shields/models/block/gravitational_mine_launcher_model.mtl'

# Fix MTL
if os.path.exists(mtl_file):
    with open(mtl_file, 'r') as f:
        mtl_text = f.read()
    
    mtl_text = mtl_text.replace('m_cb21c269-d889-eda6-3908-5acb5e19c56b', 'gravitational_mine_launcher')
    
    with open(mtl_file, 'w') as f:
        f.write(mtl_text)
    print("Fixed MTL UUID")

# Fix OBJ
if os.path.exists(obj_file):
    with open(obj_file, 'r') as f:
        obj_text = f.read()
    
    obj_text = obj_text.replace('m_cb21c269-d889-eda6-3908-5acb5e19c56b', 'gravitational_mine_launcher')
    
    with open(obj_file, 'w') as f:
        f.write(obj_text)
    print("Fixed OBJ UUID")
