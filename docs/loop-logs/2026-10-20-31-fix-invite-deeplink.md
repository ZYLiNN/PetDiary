# Loop 2026-10-20-31：修正 QR 邀請「沒有可以開啟的應用程式」

- 階段：P8 修補（使用者實測回報）
- 結果：✅ 完成，版本 0.10.1

## 問題
用手機內建相機掃邀請 QR，出現「沒有可以開啟的應用程式」。QR 內容是 `petdiary://join?g=…&c=…`，但 App 沒有宣告能開這種連結（只有 App 內的掃描器和「貼上連結」可用）。

## 修正
- `AndroidManifest.xml`：MainActivity 加上 `petdiary://join` 的 VIEW 過濾條件，並設 `singleTop`（App 已開著時直接收到連結）
- `MainActivity`：收到連結（開啟或已在前景）→ 驗證格式 → 放進 `AppContainer.incomingInvite`
- `PetDiaryApp`：有待處理邀請就自動切到「家庭群組」頁；`FamilyScreen` 直接跳出「加入家庭群組？」確認，確認後才加入

## 測試
| 項目 | 結果 |
|---|---|
| 邀請連結能被本 App 解析（PackageManager） | ✅ |
| 外部連結 → 跳出加入確認 → 確認後呼叫加入並清除待處理 | ✅ |
| FamilyScreenTest 全部 | ✅ |
| 實機：相機掃 QR → 直接開 App | ⏳ 待使用者 |

## 過程備註
- 嘗試用真的 MainActivity 做端對端測試，內容斷言都過，但 ActivityScenario 收尾逾時（無頭模擬器），改為上面兩個較穩定的測試。

## 注意
- 對方手機要先安裝 App；加入前仍需在 App 內開啟「雲端備份與同步」，否則家庭群組頁會提示先開啟（連結不會遺失，開啟後再點一次連結即可）。
