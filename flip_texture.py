import sys

try:
    from PIL import Image
    img_path = 'common/src/main/resources/assets/vs_shields/textures/entity/gravitational_mine.png'
    img = Image.open(img_path)
    img = img.transpose(Image.FLIP_TOP_BOTTOM)
    img.save(img_path)
    print("Texture flipped successfully using Pillow!")
except ImportError:
    print("Pillow not installed! I will need to flip it in a different way or install Pillow.")
