# EZ-PD Protocol Analyzer Utility v4.2.0 コミュニティパッチ v1.0p

[English](README.md) | [日本語](README.ja.md)

Infineon EZ-PD Protocol Analyzer Utility v4.2.0 と、CY4500・CY4500-EPR を含む対応 CY4500 シリーズのプロトコルアナライザー向けの非公式パッチです。パッチ本体はソースコードとして提供します。

本パッケージには、Infineon 製の実行ファイル、プラグイン JAR、ファームウェア、ドライバー、ドキュメント、逆コンパイルしたベンダーソースコードは含まれていません。対応するローカルインストール環境に変更を適用し、ファイルを置き換える前にバックアップを作成します。

## 追加機能・修正内容

### キャプチャテーブルとデータ処理

- CSV 出力の End Time とパケット行番号を修正。
- USB PD Messages、Details、Payload テーブルで、ユーザーが変更した列幅を保持。
- Payload の数値列を右揃えに変更。
- Status、SOP、Message、Msg ID、Data Role、Power Role、Obj Count、Rev に、チェックボックスによる複数選択フィルターを追加。
- 列フィルターの選択を反転する `Hide selected values` を追加。異なる列のフィルターは組み合わせて適用されます。
- キャプチャ、Open、Import、Clear 操作後もメッセージフィルターを維持。
- デバイスステータス領域に、リアルタイムの VBUS 電圧と電流を表示。
- Export、Import、Save、Exit の冗長なダイアログを削除。

### グラフ表示

- 最大 48 V までの VBUS 値を符号なしで正しく処理するよう修正。
- グラフの STOP、選択、ナビゲーション、チェックボックス、スクロールバー、レイアウト、軸の動作を修正。
- ラベル、マーカーの視認性、凡例、軸目盛りの位置、Graphical ビューのリアルタイム座標ヘッダーのレイアウトを改善。
- メインウィンドウのサイズを次回起動時まで保持。

### Triggers ビュー（オプション）

`-EnableTriggers` を指定すると、非表示の Triggers ビューを復元します。

- Start Sno、End Sno、SOP、Message Type、Object Count、Message ID のトリガー条件に対応。
- 選択した USB PD メッセージタイプを、本来の数値のままアナライザーへ送信。
- Message Type の選択リストから、予約済みの `C_RSVD*`、`D_RSVD*`、`E_RSVD*` 項目を除外。
- USB PD Messages のデコード内容は変更しません。特に、合成された VBUS イベント行の `C_RSVD0` 表記は変更しません。

トリガー出力は、アナライザー本体の GPIO（SOM、EOM、MTR）から出力されます。オシロスコープなどの外部機器で観測するための機能です。トリガー設定はパケットキャプチャとは独立しており、アナライザー本体を切断すると消去されます。

### CC Terminations ビュー（オプション・電気的な影響あり）

`-EnableTerminations` を指定すると、非表示の Terminations ビューを復元します。

> **警告：** Terminations は表示だけの設定ではありません。設定を適用すると、アナライザー本体の CC ライン抵抗が電気的に接続され、接続機器から見た Type-C の接続検出動作が変化します。

対応するアナライザー本体では、以下の CC 終端を適用できます。

| 設定 | エミュレートする役割 | 電気的な接続 |
| --- | --- | --- |
| Rp | Type-C Source | 4.7 kΩ で 3.3 V にプルアップ |
| Rd | Type-C Sink | 5.1 kΩ でプルダウン |
| Ra | 電源を必要とする Type-C ケーブル | 1 kΩ でプルダウン |
| Ra + Rd | Sink を伴う電源付きケーブル、または VCONN 給電アクセサリー | 5.1 kΩ と 1 kΩ のプルダウンを並列接続 |

Type-C の CC 接続構成と接続機器への影響を理解したうえで使用してください。設定適用の前後に、適切な測定機器で CC1 と CC2 を監視してください。Type-C Source と Type-C Sink の両方がアナライザーを介して接続されている状態では、Rp または Rd 終端を有効にしないでください。Infineon のユーザーガイドでは、Source または Sink が予測できない動作をする可能性があると警告されています。終端設定の解除には、ビュー内の Clear 機能を使用してください。

## 動作要件・互換性

- 対応する未改変の Infineon EZ-PD Protocol Analyzer Utility v4.2.0 と、互換性のある CY4500 シリーズのアナライザー。CY4500 と CY4500-EPR の両方に対応します。
- Windows PowerShell 5.1 を備えた 64 ビット版 Windows 10 または Windows 11。ランチャーは標準の `powershell.exe` を使用するため、PowerShell 7 は不要です。
- 64 ビット版 JDK 17 以降。パッチャーは JDK の `java`、`javac`、`jar`、`javap` を使用するため、JRE だけでは動作しません。
- Analyzer のインストール先ディレクトリを変更できる権限。
- パッチの適用・復元前に Analyzer を終了していること。

パッチャーは、メイン UI プラグインを書き換える前に、元のクラスが想定どおりであることを検証します。未知の構成や変更済みのインストール環境への上書きは行わず、処理を停止します。この検証に失敗した場合は、先に未改変の状態へ復元してください。

## インストール

1. Analyzer のインストール環境をバックアップし、Analyzer を終了します。
2. エクスプローラーまたはコマンドプロンプトから `Patch-Analyzer.cmd` を実行します。
3. 高度なオプションビューは、必要な場合にのみ指定します。

```bat
Patch-Analyzer.cmd
Patch-Analyzer.cmd -EnableTriggers
Patch-Analyzer.cmd -EnableTerminations
Patch-Analyzer.cmd -EnableTriggers -EnableTerminations
```

標準以外のディレクトリにインストールしている場合：

```bat
Patch-Analyzer.cmd -AppDir "D:\My Tools\EZ-PD Protocol Analyzer Utility"
```

標準のインストール先：

```text
C:\Infineon\Tools\EZ-PD Protocol Analyzer Utility
```

インストールが成功すると、Eclipse の UI レイアウトキャッシュを更新するため、パッチ適用後の Analyzer が一度起動します。

## 元の状態に戻す

Analyzer を終了してから `Restore-Analyzer.cmd` を実行してください。パッチャーが作成したプラグイン JAR のバックアップを復元し、UI レイアウトを更新した状態で Analyzer を一度起動します。

## JFreeChart 1.5.6

このパッチは、Analyzer に含まれる `jfreechart-swt-1.0.17.jar` の SWT ブリッジを維持したまま、組み込みの JFreeChart コアを 1.5.3 から 1.5.6 へ更新します。2 つの小さな互換ファサードにより、このブリッジが使用する旧 API の呼び出し口を維持します。

同梱の `third_party/jfreechart-1.5.6.jar` は、上流プロジェクトの JFreeChart です。ライセンスは GNU Lesser General Public License バージョン 2.1 以降です。ライセンスおよびソースコードについては、[third_party/README.md](third_party/README.md) と [JFreeChart の上流プロジェクト](https://github.com/jfree/jfreechart) を参照してください。

## プロジェクト構成

- `Patch-Analyzer.cmd`：通常のインストール用ランチャー。
- `Restore-Analyzer.cmd`：通常の復元用ランチャー。
- `src/`：コミュニティパッチ独自のソースコード。
- `tools/`：パッチャーがローカルでコンパイルするソース変換ツール。
- `third_party/`：別途提供される JFreeChart 1.5.6 コア JAR とその告知文書。

本プロジェクトは独立したコミュニティプロジェクトです。Infineon Technologies AG との提携関係はなく、同社による承認・サポートも受けていません。
