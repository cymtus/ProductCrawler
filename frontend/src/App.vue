<template>
  <div class="app-container">
    <!-- 顶部控制台 -->
    <el-card class="header-card">
      <div class="status-panel">
        <div class="status-info">
          <h2 style="margin: 0; color: #303133;">🛒 商品价格监控大盘</h2>
          <div style="color: #909399; font-size: 14px;">
            <span v-if="syncStatus.isRunning">
              <el-icon class="is-loading"><Refresh /></el-icon> 
              正在同步中... (进度: {{syncStatus.completedCategories}}/{{syncStatus.totalCategories}})
              <br/> 当前任务: {{syncStatus.message}}
            </span>
            <span v-else>
              <el-icon color="#67C23A"><Select /></el-icon> 
              待机中 (上次同步时间: {{formatDate(syncStatus.startTime)}})
            </span>
          </div>
          <el-progress v-if="syncStatus.isRunning" :percentage="progressPercentage" :stroke-width="8" striped striped-flow duration="10"></el-progress>
        </div>
        <el-button type="primary" size="large" :loading="syncStatus.isRunning" @click="startSync">
          <el-icon><VideoPlay /></el-icon> 立即同步
        </el-button>
      </div>
    </el-card>

    <el-card class="data-card" body-style="padding-top: 0">
      <el-tabs v-model="activeTab" @tab-change="onTabChange" class="custom-tabs">
        <!-- 第一个 Tab：监控大盘 -->
        <el-tab-pane label="监控大盘" name="dashboard">
          <div style="display: flex; gap: 10px; margin: 15px 0;">
            <el-input v-model="searchForm.keyword" placeholder="搜索商品名称" style="width: 250px;" clearable @clear="handleSearch">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="searchForm.categoryId" placeholder="全部分类" clearable filterable style="width: 180px;" @change="handleSearch">
              <el-option v-for="(val, key) in categories" :key="key" :label="val" :value="key" />
            </el-select>
            <el-select v-model="searchForm.status" placeholder="商品状态" clearable style="width: 150px;" @change="handleSearch">
              <el-option label="在售 🟢" :value="1" />
              <el-option label="下架 🔴" :value="0" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>

          <el-table :data="skus" style="width: 100%" v-loading="loading" border stripe>
            <el-table-column prop="id" label="SKU ID" width="120"></el-table-column>
            <el-table-column prop="name" label="商品名称" min-width="200"></el-table-column>
            <el-table-column label="一级分类" width="120">
              <template #default="scope">
                <el-tag type="info" effect="plain">{{ categories[scope.row.categoryId1] || scope.row.categoryId1 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="价格" width="150">
              <template #default="scope">
                <span style="color: #F56C6C; font-weight: bold;">¥ {{ scope.row.stdSalePrice }}</span>
                <span style="color: #909399; font-size: 12px;"> /{{ scope.row.saleUnitName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
                  {{ scope.row.status === 1 ? '在售' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后同步时间" width="180">
              <template #default="scope">
                {{ formatDate(scope.row.lastSyncTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="viewHistory(scope.row)">
                  <el-icon><TrendCharts /></el-icon> 价格走势
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 大盘分页 -->
          <div style="display: flex; justify-content: flex-end; margin-top: 20px;">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="pagination.total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </el-tab-pane>

        <!-- 第二个 Tab：待办任务 -->
        <el-tab-pane name="tasks">
          <template #label>
            <span style="display: flex; align-items: center; gap: 5px;">
              🔔 待办提醒
              <el-badge :value="taskPagination.total" v-if="taskPagination.total > 0" class="item" />
            </span>
          </template>

          <div style="margin: 15px 0; color: #909399; font-size: 14px;">
            💡 这里显示爬虫监控到的最近变动商品。您可以去自有系统调整对应信息后，在此将其标记为“已处理”。
          </div>

          <el-table :data="tasks" style="width: 100%" v-loading="taskLoading" border>
            <el-table-column prop="id" label="SKU ID" width="120"></el-table-column>
            <el-table-column prop="name" label="商品名称" min-width="200"></el-table-column>
            <el-table-column label="变更类型" width="120">
              <template #default="scope">
                <el-tag :type="getChangeTypeColor(scope.row.changeType)" effect="dark">
                  {{ scope.row.changeType || '未知变更' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最新价格" width="120">
              <template #default="scope">
                <span style="color: #F56C6C; font-weight: bold;">¥ {{ scope.row.stdSalePrice }}</span>
              </template>
            </el-table-column>
            <el-table-column label="当前状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ scope.row.status === 1 ? '在售' : '已下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变动时间" width="180">
              <template #default="scope">
                {{ formatDate(scope.row.lastSyncTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="viewHistory(scope.row)">
                  <el-icon><TrendCharts /></el-icon> 走势
                </el-button>
                <el-button type="success" size="small" plain @click="resolveTask(scope.row.id)">
                  <el-icon><Check /></el-icon> 标记已处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 待办分页 -->
          <div style="display: flex; justify-content: flex-end; margin-top: 20px;">
            <el-pagination
              v-model:current-page="taskPagination.page"
              v-model:page-size="taskPagination.size"
              :page-sizes="[10, 20, 50]"
              layout="total, prev, pager, next"
              :total="taskPagination.total"
              @size-change="handleTaskSizeChange"
              @current-change="handleTaskCurrentChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 走势图抽屉 -->
    <el-drawer v-model="drawerVisible" :title="currentSku ? currentSku.name + ' - 历史走势' : '价格走势'" size="50%" @opened="initChart">
      <div v-loading="chartLoading" class="chart-container" id="priceChart"></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import * as echarts from 'echarts'

const activeTab = ref('dashboard')

// 大盘相关
const loading = ref(false)
const skus = ref([])
const categories = ref({}) // 分类字典
const searchForm = reactive({ keyword: '', status: null, categoryId: null })
const pagination = reactive({ page: 1, size: 20, total: 0 })

// 待办相关
const taskLoading = ref(false)
const tasks = ref([])
const taskPagination = reactive({ page: 1, size: 20, total: 0 })

const syncStatus = reactive({
  isRunning: false, totalCategories: 0, completedCategories: 0, message: 'Idle', startTime: 0
})
let statusTimer = null

const drawerVisible = ref(false)
const currentSku = ref(null)
const chartLoading = ref(false)
let chartInstance = null

const progressPercentage = computed(() => {
  if (syncStatus.totalCategories === 0) return 0
  return Math.floor((syncStatus.completedCategories / syncStatus.totalCategories) * 100)
})

const formatDate = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', { hour12: false })
}

// 获取后端配置的分类字典
const fetchCategories = async () => {
  try {
    const { data } = await axios.get('/ypc/categories')
    categories.value = data
  } catch (error) {
    console.error('获取分类失败', error)
  }
}

// 获取商品列表（带分页）
const fetchSkus = async () => {
  loading.value = true
  try {
    const params = {
      ...searchForm,
      page: pagination.page,
      size: pagination.size
    }
    const { data } = await axios.get('/ypc/skus', { params })
    skus.value = data.records
    pagination.total = data.total
  } catch (error) {
    ElMessage.error('获取商品列表失败')
  } finally {
    loading.value = false
  }
}

// 获取待办任务列表（仅查 isProcessed = 0 的数据）
const fetchTasks = async () => {
  taskLoading.value = true
  try {
    const params = {
      isProcessed: 0,
      page: taskPagination.page,
      size: taskPagination.size
    }
    const { data } = await axios.get('/ypc/skus', { params })
    tasks.value = data.records
    taskPagination.total = data.total
  } catch (error) {
    ElMessage.error('获取待办任务失败')
  } finally {
    taskLoading.value = false
  }
}

// 标记任务已处理
const resolveTask = async (skuId) => {
  try {
    await axios.post(`/ypc/skus/${skuId}/resolve`)
    ElMessage.success('处理成功，已从待办移除')
    fetchTasks() // 刷新待办列表
    if (activeTab.value === 'dashboard') fetchSkus()
  } catch (error) {
    ElMessage.error('处理失败')
  }
}

// Tab 切换逻辑
const onTabChange = (name) => {
  if (name === 'dashboard') fetchSkus()
  if (name === 'tasks') fetchTasks()
}

// 变更类型标签颜色
const getChangeTypeColor = (type) => {
  if (type === '新上架') return 'success'
  if (type === '已下架') return 'danger'
  if (type === '价格变动') return 'warning'
  return 'info'
}

// 搜索触发
const handleSearch = () => {
  pagination.page = 1
  fetchSkus()
}

// 大盘分页
const handleSizeChange = (val) => {
  pagination.size = val
  pagination.page = 1
  fetchSkus()
}
const handleCurrentChange = (val) => {
  pagination.page = val
  fetchSkus()
}

// 待办分页
const handleTaskSizeChange = (val) => {
  taskPagination.size = val
  taskPagination.page = 1
  fetchTasks()
}
const handleTaskCurrentChange = (val) => {
  taskPagination.page = val
  fetchTasks()
}

// 轮询同步状态
const pollStatus = async () => {
  try {
    const { data } = await axios.get('/ypc/status')
    Object.assign(syncStatus, data)
    if (!syncStatus.isRunning && statusTimer) {
      clearInterval(statusTimer)
      statusTimer = null
      ElMessage.success('同步已完成！')
      fetchSkus()
    }
  } catch (error) { 
    console.error('获取状态失败', error) 
  }
}

// 触发同步
const startSync = async () => {
  try {
    await axios.post('/ypc/sync')
    ElMessage.success('同步任务已投递')
    syncStatus.isRunning = true
    if (!statusTimer) statusTimer = setInterval(pollStatus, 2000)
  } catch (error) {
    ElMessage.error('触发同步失败')
  }
}

// 查看历史
const viewHistory = (sku) => {
  currentSku.value = sku
  drawerVisible.value = true
}

// 渲染图表
const initChart = async () => {
  if (chartInstance) { chartInstance.dispose() }
  await nextTick()
  const chartDom = document.getElementById('priceChart')
  if (!chartDom) return
  
  chartInstance = echarts.init(chartDom)
  chartLoading.value = true
  
  try {
    const { data } = await axios.get(`/ypc/skus/${currentSku.value.id}/history`)
    
    const option = {
      tooltip: { trigger: 'axis', formatter: '时间: {b}<br/>价格: ¥{c}' },
      xAxis: { 
        type: 'category', 
        data: data.map(item => formatDate(item.createTime)),
        axisLabel: { rotate: 45 }
      },
      yAxis: { type: 'value', min: 'dataMin' },
      series: [{
        data: data.map(item => item.price),
        type: 'line',
        smooth: true,
        itemStyle: { color: '#409EFF' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.3)' },
            { offset: 1, color: 'rgba(64,158,255,0.1)' }
          ])
        }
      }]
    }
    chartInstance.setOption(option)
  } catch (error) {
    ElMessage.error('获取历史走势失败')
  } finally {
    chartLoading.value = false
  }
}

onMounted(() => {
  fetchCategories()
  fetchSkus()
  fetchTasks() // 初始也查一次待办，用于显示小红点
  pollStatus()
})

onUnmounted(() => { 
  if (statusTimer) clearInterval(statusTimer) 
})
</script>

<style>
body { 
  margin: 0; 
  background-color: #f5f7fa; 
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif; 
}
.app-container { padding: 20px; max-width: 1200px; margin: 0 auto; }
.header-card { margin-bottom: 20px; border-radius: 12px; border: none; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.data-card { border-radius: 12px; border: none; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.status-panel { display: flex; align-items: center; justify-content: space-between; }
.status-info { display: flex; flex-direction: column; gap: 8px; }
.chart-container { height: 400px; width: 100%; }
</style>
