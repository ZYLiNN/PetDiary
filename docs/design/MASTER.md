# PetDiary 設計系統 — C 奶茶柔和

> 所有畫面以此為準。預覽：`docs/design/style-preview.html`（選 C）。
> 實作對應：Compose Material 3 `ColorScheme` / `Typography` / `Shapes`。

## 色彩

| Token | 淺色 | 深色 | 用途 |
|---|---|---|---|
| primary | `#8B5E3C` | `#D4A373` | 主要按鈕、FAB、選取的日期 |
| onPrimary | `#FFFFFF` | `#181512` | primary 上的文字和圖示 |
| secondary（accent） | `#6B8F71` | `#8DB394` | 次要強調（鼠尾草綠） |
| background | `#F7F2EC` | `#181512` | 頁面背景 |
| surface | `#FFFDFA` | `#24201C` | 卡片、面板 |
| onSurface（text） | `#3B2A1E` | `#F3ECE4` | 主要文字 |
| onSurfaceVariant（muted） | `#74655A` | `#BBAE9F` | 次要文字（P0 對比度測試後由 #7D6E62 調深） |
| outline（line） | `#E6D9CB` | `#3A332C` | 邊框、分隔線 |
| surfaceVariant（chip） | `#EFE5DA` | `#2F2924` | 標籤、圖示底色 |

### 紀錄類型顏色（不隨風格改變；深色模式用同色系的亮一階版本）

| 類型 | 淺色 | 深色 | 圖示（Material Symbols 對應） |
|---|---|---|---|
| 餵食 | `#D97706` | `#D97706` | restaurant |
| 藥物 | `#7C3AED` | `#A78BFA` | medication |
| 疫苗 | `#2563EB` | `#60A5FA` | vaccines |
| 驅蟲 | `#16A34A` | `#16A34A` | pest_control |
| 體重 | `#0891B2` | `#0891B2` | monitor_weight |
| 就診 | `#DC2626` | `#F87171` | stethoscope |
| 美容 | `#DB2777` | `#F472B6` | content_cut |
| 異常狀況 | `#EA580C` | `#EA580C` | warning |

- 卡片：左側 5dp 色條 ＋ 14% 透明度的圖示底色。**顏色一定搭配圖示**，不能只靠顏色區分。
- 對比度由單元測試 `ContrastTest` 自動檢查：文字 ≥ 4.5:1、類型圖示 ≥ 3:1。

## 字型
- 中文：Noto Sans TC（系統內建的思源黑體）
- 英文與數字：Nunito（圓潤友善）
- 基本字級 16sp，行高 1.5；最小字級 12sp

## 形狀與間距
- 圓角：寵物方格 26dp、紀錄卡片 14dp、FAB 18dp、底部面板 24dp、標籤 全圓
- 間距：4 / 8 / 12 / 16 / 24 / 32dp
- 最小點擊範圍 48×48dp

## 互動原則
- 簡單直覺優先：能少一步就少一步（見 PLAN.md 第 11 節 S1～S12）
- 刪除不跳確認，改用「復原」提示；只有「永久刪除」才確認
- 動畫低調：150～250ms，尊重系統的「減少動態效果」設定
