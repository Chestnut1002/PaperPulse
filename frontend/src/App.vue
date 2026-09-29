<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <!-- 统一指定中文 locale,组件内部文案与校验提示就不用逐个配置 -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
