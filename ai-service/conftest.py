"""存在即有用:conftest.py 所在的目录会被 pytest 加进模块搜索路径,
测试里才能 `from app.xxx import ...`。删掉这个文件测试会因为找不到 app 包而失败。
"""
