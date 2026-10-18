# PetDiary — 專案規則

寵物日常紀錄 Android App。需求與決策見 `docs/PLAN.md`，驗收條件見 `docs/TEST_CHECKLIST.md`。

## 溝通
- 使用繁體中文；先講結論，再依序說明。
- 不確定的事先問使用者，不要自行決定。

## 產品原則：簡單直覺優先
- 以「方便」為出發點，能少一步就少一步。
- 發現可以簡化或合併的 UI/UX 或功能，要主動提出，由使用者決定，不要自行改動已確認的需求。

## 技術
- Kotlin + Jetpack Compose（Material 3），Room 本機資料庫，之後接 Firebase。
- 資料結構從第一天就要能同步：UUID 主鍵、`updatedAt`、軟刪除旗標。
- 折疊螢幕必須支援（OPPO Find N6、Samsung Fold 8 寬折疊）：依寬度＋高度的 Window Size Class 排版，不鎖定方向，折疊/攤開時狀態不能遺失。
- 模擬器：`Pixel_10_Pro_Fold`；JDK 使用 Android Studio 內建 JBR。

## 版本控制（必須遵守）
- **每一次修改都要 commit**，訊息使用 Conventional Commits 格式（`feat:`、`fix:`、`docs:`、`test:`、`refactor:`、`chore:`）。
- commit 後推送到 GitHub（`origin` = https://github.com/ZYLiNN/PetDiary ，Private）。
- 不要把 keystore、`google-services.json`、`local.properties` commit 進去。

## Loop 紀錄（必須遵守）
- 每一輪 loop 結束時，在 `docs/loop-logs/` 依 `README.md` 的範本新增紀錄，並更新 INDEX。
- 內容包含：修改了什麼、對應 commit、測試清單結果、待修正事項、下一輪計畫、需要使用者決定的事。
- loop log 本身也要 commit。
