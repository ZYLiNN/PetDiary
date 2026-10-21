# Loop 2026-10-21-34：P13 相簿分頁、點連結加入家庭、提醒寵物選擇、行事曆版面

- 階段：P13（D79、D80）＋使用者回饋（新增提醒的寵物選擇、行事曆下方太擠）
- 對應 commit：本 log 的 commit；tag `v0.13.0-p13`
- 結果：✅ 完成

## 這次做了什麼
- **相簿分頁**取代「提醒」分頁：照片格狀 / 檔案清單、依月份、寵物篩選、放大後「看紀錄 / 下載 / 分享」、下載中佔位、垃圾桶的不顯示
- **提醒清單**改從行事曆右上角鈴鐺開啟（← 返回）
- **點連結加入家庭**：邀請連結改為 `https://petdiary-878a6.web.app/join?openExternalBrowser=1#g=…&c=…`；Firebase Hosting 說明頁（開啟 App 按鈕、複製連結、CSP 等安全標頭）＋ `assetlinks.json`，**已部署並經 Google Digital Asset Links API 驗證通過**；App 加上 https App Links（autoVerify）；舊 `petdiary://` 連結仍可用
- **擁有者通知**：有人加入家庭群組時跳出通知（不需伺服器，比對上次看到的成員）
- **新增提醒寵物選擇**：選到的有 ✓，最後一隻不能取消
- **行事曆版面**：寵物篩選移到右上角「篩選」選單（D81），格子 66→60dp；截圖 `screenshots/2026-10-21-34-calendar-filter-menu.png`
- 版本 0.13.0（versionCode 15）；APK `dist/PetDiary-v0.13.0-P13-debug.apk`

## 測試結果
| 項目 | 結果 | 備註 |
|---|---|---|
| 單元測試 | ✅ | 155 個（新增：邀請連結格式、擁有者通知、提醒寵物） |
| 模擬器完整測試 | ✅ | 256 個、0 失敗（新增相簿 6 個、提醒清單導覽、https 連結由本 App 開啟、篩選選單） |
| 真 Firebase | ✅ | CloudSyncLiveTest、FamilyLiveTest、FileDeleteLiveTest |
| 網頁部署 | ✅ | assetlinks.json 200 / application/json；Google 驗證回傳本 App 指紋 |
| P13-08 實機 | ⏳ | 待使用者（MANUAL_TEST_v0.13.0.md），尤其 LINE 點連結的實際行為 |

## 過程中的問題與修正
- 中文語系下 keytool 印憑證會出錯，改用 `-J-Duser.language=en` 取得 SHA-256。
- 截圖用暫時測試的類別名稱寫錯一次，修正後截圖完成並刪除暫時測試。
- Hosting 預設會忽略 `.` 開頭的檔案（`.well-known`），firebase.json 改成只忽略 node_modules。

## 需要使用者決定 / 提醒
- D81～D83（行事曆篩選方式、提醒清單位置、沒裝 App 的人看到的說明）。
- 正式簽章（D9）確定後，要把新指紋加進 `assetlinks.json` 重新部署，否則正式版點連結不會直接開 App。
- 建議設 Google Cloud 預算警示。
- 實機測試：MANUAL_TEST v0.11.0～v0.13.0。
