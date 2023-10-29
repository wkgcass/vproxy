package io.vproxy.ui.calculator;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfx.manager.font.FontManager;
import io.vproxy.vfx.ui.alert.SimpleAlert;
import io.vproxy.vfx.ui.button.FusionButton;
import io.vproxy.vfx.ui.layout.HPadding;
import io.vproxy.vfx.ui.layout.VPadding;
import io.vproxy.vfx.ui.scene.VScene;
import io.vproxy.vfx.ui.scene.VSceneGroup;
import io.vproxy.vfx.ui.scene.VSceneRole;
import io.vproxy.vfx.ui.scene.VSceneShowMethod;
import io.vproxy.vfx.ui.wrapper.FusionW;
import io.vproxy.vfx.ui.wrapper.ThemeLabel;
import io.vproxy.vfx.util.FXUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class IPv4CalculatorScene extends VScene {
    static final int VALUE_WIDTH = 435;
    private static final int LINE_PADDING = 10;
    private static final int KV_PADDING = 20;
    private static final double W_LABEL_PADDING = 10;
    private static final double W_KV_PADDING = KV_PADDING - W_LABEL_PADDING;

    private final TextField cidr = new TextField() {{
        setPrefWidth(VALUE_WIDTH);
    }};
    private final TextField addressBin = new TextField() {{
        setPrefWidth(VALUE_WIDTH);
    }};
    private final TextField bigEndian = new TextField() {{
        setPrefWidth(VALUE_WIDTH);
    }};
    private final TextField littleEndian = new TextField() {{
        setPrefWidth(VALUE_WIDTH);
    }};
    private final ThemeLabel maskBin = new ValueLabel();
    private final ThemeLabel mask = new ValueLabel();
    private final ThemeLabel maskIpFormat = new ValueLabel();
    private final ThemeLabel firstAddress = new ValueLabel();
    private final ThemeLabel lastAddress = new ValueLabel();
    private final ThemeLabel networkAddress = new ValueLabel();
    private final ThemeLabel broadcastAddress = new ValueLabel();
    private final ThemeLabel availableCount = new ValueLabel();
    private final ThemeLabel totalCount = new ValueLabel();

    final FusionButton sceneButton;
    VSceneGroup sceneGroup;
    List<VScene> allScenes;
    List<Node> buttonsParentChildren;

    public IPv4CalculatorScene() {
        this(null, null, null);
    }

    public IPv4CalculatorScene(VSceneGroup sceneGroup0, List<VScene> allScenes0, List<Node> buttonsParentChildren0) {
        super(VSceneRole.MAIN);
        enableAutoContentWidthHeight();

        this.sceneGroup = sceneGroup0;
        this.allScenes = allScenes0;
        this.buttonsParentChildren = buttonsParentChildren0;

        var cidrLabel = new DescLabel("IPv4") {{
            setPadding(new Insets(5.5, 0, 0, 0));
        }};
        var addressBinaryLabel = new DescLabel("Binary Address") {{
            setPadding(new Insets(5.5, 0, 0, 0));
        }};
        var bigEndianLabel = new DescLabel("Big Endian") {{
            setPadding(new Insets(5.5, 0, 0, 0));
        }};
        var littleEndianLabel = new DescLabel("Little Endian") {{
            setPadding(new Insets(5.5, 0, 0, 0));
        }};
        var maskBinaryLabel = new DescLabel("Binary Mask");
        var maskLabel = new DescLabel("Mask");
        var maskIpFormatLabel = new DescLabel("Mask in IP Format");
        var networkAddressLabel = new DescLabel("Network Address");
        var firstAddressLabel = new DescLabel("First Address");
        var lastAddressLabel = new DescLabel("Last Address");
        var broadcastAddressLabel = new DescLabel("Broadcast Address");
        var availableCountLabel = new DescLabel("Available Count");
        var totalCountLabel = new DescLabel("Total Count");

        var cidrInput = new FusionW(cidr);
        FontManager.get().setFont(cidr);
        FontManager.get().setFont(cidrInput.getLabel());
        cidrInput.getLabel().setPrefWidth(VALUE_WIDTH);
        cidrInput.getLabel().setPadding(new Insets(0, 0, 0, 10));

        var addressBinInput = new FusionW(addressBin);
        FontManager.get().setFont(addressBin);
        FontManager.get().setFont(addressBinInput.getLabel());
        addressBinInput.getLabel().setPrefWidth(VALUE_WIDTH);
        addressBinInput.getLabel().setPadding(new Insets(0, 0, 0, 10));

        var bigEndianInput = new FusionW(bigEndian);
        FontManager.get().setFont(bigEndian);
        FontManager.get().setFont(bigEndianInput.getLabel());
        bigEndianInput.getLabel().setPrefWidth(VALUE_WIDTH);
        bigEndianInput.getLabel().setPadding(new Insets(0, 0, 0, 10));

        var littleEndianInput = new FusionW(littleEndian);
        FontManager.get().setFont(littleEndian);
        FontManager.get().setFont(littleEndianInput.getLabel());
        littleEndianInput.getLabel().setPrefWidth(VALUE_WIDTH);
        littleEndianInput.getLabel().setPadding(new Insets(0, 0, 0, 10));

        sceneButton = new FusionButton() {{
            setDisableAnimation(true);
            setPrefWidth(180);
        }};
        sceneButton.setOnAction(e -> {
            if (sceneGroup.getCurrentMainScene() == this)
                return;
            var currIndex = allScenes.indexOf(sceneGroup.getCurrentMainScene());
            var selfIndex = allScenes.indexOf(this);
            sceneGroup.show(this, currIndex < selfIndex ? VSceneShowMethod.FROM_RIGHT : VSceneShowMethod.FROM_LEFT);
        });
        getNode().sceneProperty().addListener((ob, old, now) -> {
            sceneButton.setDisable(now != null);
            if (now != null) {
                return;
            }
            if (!cidr.getText().isBlank() || !addressBin.getText().isBlank()) {
                return;
            }
            buttonsParentChildren.remove(sceneButton);
        });

        var rootVBox = new VBox();
        rootVBox.getChildren().addAll(
            new HBox(cidrLabel, new HPadding(W_KV_PADDING), cidrInput),
            new HBox(addressBinaryLabel, new HPadding(W_KV_PADDING), addressBinInput),
            new HBox(bigEndianLabel, new HPadding(W_KV_PADDING), bigEndianInput),
            new HBox(littleEndianLabel, new HPadding(W_KV_PADDING), littleEndianInput),
            new VPadding(LINE_PADDING),
            new HBox(maskBinaryLabel, new HPadding(KV_PADDING), maskBin),
            new VPadding(LINE_PADDING),
            new HBox(maskLabel, new HPadding(KV_PADDING), mask),
            new VPadding(LINE_PADDING),
            new HBox(maskIpFormatLabel, new HPadding(KV_PADDING), maskIpFormat),
            new VPadding(LINE_PADDING),
            new HBox(networkAddressLabel, new HPadding(KV_PADDING), networkAddress),
            new VPadding(LINE_PADDING),
            new HBox(firstAddressLabel, new HPadding(KV_PADDING), firstAddress),
            new VPadding(LINE_PADDING),
            new HBox(lastAddressLabel, new HPadding(KV_PADDING), lastAddress),
            new VPadding(LINE_PADDING),
            new HBox(broadcastAddressLabel, new HPadding(KV_PADDING), broadcastAddress),
            new VPadding(LINE_PADDING),
            new HBox(availableCountLabel, new HPadding(KV_PADDING), availableCount),
            new VPadding(LINE_PADDING),
            new HBox(totalCountLabel, new HPadding(KV_PADDING), totalCount)
        );

        getContentPane().getChildren().add(rootVBox);
        FXUtils.observeWidthHeightCenter(getContentPane(), rootVBox);

        cidr.textProperty().addListener((ob, old, now) -> cidrUpdated());
        addressBin.textProperty().addListener((ob, old, now) -> addressBinUpdated());
        bigEndian.textProperty().addListener((ob, old, now) -> bigEndianUpdated());
        littleEndian.textProperty().addListener((ob, old, now) -> littleEndianUpdated());

        cidr.setText("192.168.0.1/24");
    }

    private boolean isUpdating = false;

    private void finishUpdating() {
        Platform.runLater(() -> isUpdating = false);
    }

    private void cidrUpdated() {
        if (isUpdating) {
            return;
        }
        isUpdating = true;
        try {
            IP ip;
            try {
                ip = IP.from(cidr.getText());
            } catch (Exception e) {
                cidrUpdatedWithNetwork();
                return;
            }
            if (!(ip instanceof IPv4)) {
                clearForCidr();
                SimpleAlert.show(Alert.AlertType.WARNING, "This calculator is only made for IPv4");
                return;
            }
            updateActive(ip, 32, false, true, true, true);
            updatePassive(ip, 32);
        } finally {
            finishUpdating();
        }
    }

    private void cidrUpdatedWithNetwork() {
        var text = cidr.getText();
        IP ip;
        int mask;
        try {
            if (!text.contains("/")) {
                throw new Exception(null, null, false, false) {
                };
            }
            var split = text.split("/");
            if (split.length != 2) {
                throw new Exception(null, null, false, false) {
                };
            }
            ip = IP.from(split[0]);
            mask = Integer.parseInt(split[1]);
            if (mask < 0 || mask > 32) {
                throw new Exception(null, null, false, false) {
                };
            }
        } catch (Exception e) {
            clearForCidr();
            return;
        }
        if (!(ip instanceof IPv4)) {
            clearForCidr();
            SimpleAlert.show(Alert.AlertType.WARNING, "This calculator is only made for IPv4");
            return;
        }
        updateActive(ip, mask, false, true, true, true);
        updatePassive(ip, mask);
    }

    private void addressBinUpdated() {
        if (isUpdating) {
            return;
        }
        isUpdating = true;
        try {
            var chars = addressBin.getText().toCharArray();
            int count = 0;
            var sb = new StringBuilder();
            for (var c : chars) {
                if (c != '0' && c != '1' && c != ' ') {
                    clearForAddressBin();
                    return;
                }
                if (c == '0' || c == '1') {
                    ++count;
                    sb.append(c);
                }
            }
            if (count > 32 || count == 0) {
                clearForAddressBin();
                if (count > 32) {
                    SimpleAlert.show(Alert.AlertType.WARNING, "This calculator is only made for IPv4");
                }
                return;
            }

            var n = (int) Long.parseLong(sb.toString(), 2);
            formatAndSetAddressBinText(n);
            var maskStr = mask.getText();
            if (maskStr.isBlank()) {
                maskStr = "32";
            }
            var mask = Integer.parseInt(maskStr);
            var ip = IP.from(intToV4Bytes(n));
            cidr.setText(ip.formatToIPString() + "/" + maskStr);
            updateActive(ip, mask,
                true,
                false,
                true,
                true);
            updatePassive(ip, mask);
        } finally {
            finishUpdating();
        }
    }

    private void bigEndianUpdated() {
        if (isUpdating) {
            return;
        }
        isUpdating = true;
        try {
            var str = bigEndian.getText();
            long n;
            try {
                n = Long.parseLong(str);
            } catch (NumberFormatException e) {
                clearForBigEndian();
                return;
            }
            if (n < 0 && n >= Integer.MIN_VALUE) {
                n = n & 0xffffffffL;
            }
            if (n > 0xffffffffL || n < 0) {
                clearForBigEndian();
                return;
            }
            var maskStr = mask.getText();
            if (maskStr.isBlank()) {
                maskStr = "32";
            }
            var mask = Integer.parseInt(maskStr);
            var ip = IP.from(intToV4Bytes((int) n));
            updateActive(ip, mask, true, true, false, true);
            updatePassive(ip, mask);
        } finally {
            finishUpdating();
        }
    }

    private void littleEndianUpdated() {
        if (isUpdating) {
            return;
        }
        isUpdating = true;
        try {
            var str = littleEndian.getText();
            long n;
            try {
                n = Long.parseLong(str);
            } catch (NumberFormatException e) {
                clearForLittleEndian();
                return;
            }
            if (n < 0 && n >= Integer.MIN_VALUE) {
                n = n & 0xffffffffL;
            }
            if (n > 0xffffffffL || n < 0) {
                clearForLittleEndian();
                return;
            }
            var maskStr = mask.getText();
            if (maskStr.isBlank()) {
                maskStr = "32";
            }
            var mask = Integer.parseInt(maskStr);
            var b = intToV4Bytes((int) n);
            var tmp = b[0];
            b[0] = b[3];
            b[3] = tmp;
            tmp = b[1];
            b[1] = b[2];
            b[2] = tmp;
            var ip = IP.from(b);
            updateActive(ip, mask, true, true, true, false);
            updatePassive(ip, mask);
        } finally {
            finishUpdating();
        }
    }

    private void formatAndSetAddressBinText(int n) {
        addressBin.setText(formatBinary(ByteArray.allocate(4).int32(0, n)));
    }

    private void clearActive(boolean cidr, boolean addressBin, boolean bigEndian, boolean littleEndian) {
        if (cidr)
            this.cidr.setText("");
        if (addressBin)
            this.addressBin.setText("");
        if (bigEndian)
            this.bigEndian.setText("");
        if (littleEndian)
            this.littleEndian.setText("");
    }

    private void clearForCidr() {
        clearActive(false, true, true, true);
        clearPassiveFields();
    }

    private void clearForAddressBin() {
        clearActive(true, false, true, true);
        clearPassiveFields();
    }

    private void clearForBigEndian() {
        clearActive(true, true, false, true);
        clearPassiveFields();
    }

    private void clearForLittleEndian() {
        clearActive(true, true, true, false);
        clearPassiveFields();
    }

    public void updateActive(IP ip, int mask,
                             boolean cidr, boolean addressBin, boolean bigEndian, boolean littleEndian) {
        if (cidr)
            this.cidr.setText(ip.formatToIPString() + "/" + mask);
        if (addressBin)
            updateAddressBin(ip);
        if (bigEndian)
            updateBigEndian(ip);
        if (littleEndian)
            updateLittleEndian(ip);
    }

    private void updatePassive(IP ip, int mask) {
        var net = Network.eraseToNetwork(ip, mask);
        int networkIdInt = ((IPv4) net.getIp()).getIPv4Value();
        int broadcastAddressInt = networkIdInt | (0xffffffff >>> mask);
        byte[] broadcastAddressBytes = intToV4Bytes(broadcastAddressInt);
        var broadcastAddress = IP.from(broadcastAddressBytes);

        updateButton(ip, mask);
        updateMaskBin(mask);
        updateMask(mask);
        updateMaskIpFormat(mask);
        updateNetworkAddress(net.getIp(), mask);
        updateFirstAddress(mask == 32 ? ip : IP.from(intToV4Bytes(networkIdInt + (mask == 31 ? 0 : 1))));
        updateLastAddress(mask == 32 ? ip : IP.from(intToV4Bytes(broadcastAddressInt - (mask == 31 ? 0 : 1))));
        updateBroadcastAddress(broadcastAddress, mask);
        updateAvailableCount((1L << (32 - mask)) - (mask > 30 ? 0 : 2));
        updateTotalCount(1L << (32 - mask));
    }

    private void updateButton(IP ip, int mask) {
        sceneButton.setText(ip.formatToIPString() + "/" + mask);
    }

    private byte[] intToV4Bytes(int n) {
        return new byte[]{
            (byte) ((n >> 24) & 0xff),
            (byte) ((n >> 16) & 0xff),
            (byte) ((n >> 8) & 0xff),
            (byte) (n & 0xff),
        };
    }

    private String formatBinary(ByteArray b) {
        var builder = new StringBuilder();
        for (var i = 0; i < b.length(); ++i) {
            int n = b.get(i) & 0xff;
            var binBuilder = new StringBuilder(Integer.toBinaryString(n));
            if (binBuilder.length() < 8) {
                binBuilder.insert(0, "0".repeat(8 - binBuilder.length()));
            }
            if (i != 0) {
                builder.append("  ");
            }
            builder.append(binBuilder.subSequence(0, 4));
            builder.append(" ");
            builder.append(binBuilder.subSequence(4, 8));
        }
        return builder.toString();
    }

    private void updateAddressBin(IP ip) {
        var b = ip.bytes;
        addressBin.setText(formatBinary(b));
    }

    private void updateBigEndian(IP ip) {
        bigEndian.setText("" + ((long) ip.bytes.int32(0) & 0xffffffffL));
    }

    private void updateLittleEndian(IP ip) {
        var a = ip.bytes.get(0);
        var b = ip.bytes.get(1);
        var c = ip.bytes.get(2);
        var d = ip.bytes.get(3);
        var n = ((long) (d & 0xff) << 24) | ((c & 0xff) << 16) | ((b & 0xff) << 8) | (a & 0xff);
        littleEndian.setText("" + n);
    }

    private void updateMaskBin(int mask) {
        var n = Utils.maskNumberToInt(mask);
        var b = ByteArray.allocate(4);
        b.int32(0, n);
        maskBin.setText(formatBinary(b));
    }

    private void updateMask(int mask) {
        this.mask.setText("" + mask);
    }

    private void updateMaskIpFormat(int mask) {
        var n = Utils.maskNumberToInt(mask);
        var b = ByteArray.allocate(4);
        b.int32(0, n);
        this.maskIpFormat.setText(IP.from(b.toJavaArray()).formatToIPString());
    }

    private void updateFirstAddress(IP ip) {
        firstAddress.setText(ip.formatToIPString());
    }

    private void updateLastAddress(IP ip) {
        lastAddress.setText(ip.formatToIPString());
    }

    private void updateNetworkAddress(IP ip, int mask) {
        if (mask == 32) {
            networkAddress.setText("One Host");
        } else if (mask == 31) {
            networkAddress.setText("Point-to-Point Link");
        } else {
            networkAddress.setText(ip.formatToIPString());
        }
    }

    private void updateBroadcastAddress(IP ip, int mask) {
        if (mask == 32) {
            broadcastAddress.setText("One Host");
        } else if (mask == 31) {
            broadcastAddress.setText("Point-to-Point Link");
        } else {
            broadcastAddress.setText(ip.formatToIPString());
        }
    }

    private void updateAvailableCount(long count) {
        availableCount.setText("" + count);
    }

    private void updateTotalCount(long count) {
        totalCount.setText("" + count);
    }

    private void clearPassiveFields() {
        sceneButton.setText("invalid");
        maskBin.setText("");
        mask.setText("");
        maskIpFormat.setText("");
        firstAddress.setText("");
        lastAddress.setText("");
        networkAddress.setText("");
        broadcastAddress.setText("");
        availableCount.setText("");
        totalCount.setText("");
    }
}
