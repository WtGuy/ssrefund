package com.serendipitymc.refund.managers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.serendipitymc.refund.refund.refund;
import com.serendipitymc.refund.util.SSUtil;

public class RefundHandler {
	public static refund plugin = refund.getInstance();
    private Connection connection;
    
	public int getExecutableAmount() throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT count(refund_id) FROM " + thRefund + " WHERE status = 'signed off'");
		int refundRequests = 0;
		while (rs.next()) {
			refundRequests = rs.getInt(1);
		}
		rs.close();
		return refundRequests;	
		
	}
	
	private Connection establishConnection() {
		try {
			if (connection != null)
				if (connection.isClosed() == false)
					return connection;
			String hostname = plugin.getConfig().getString("mysql.ip");
			String username = plugin.getConfig().getString("mysql.user");
			String password = plugin.getConfig().getString("mysql.password");
			String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
			String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
			connection = DriverManager.getConnection("jdbc:mysql://" + hostname, username, password);
			Statement sh = connection.createStatement();
			sh.execute("CREATE TABLE IF NOT EXISTS " + thRefund + "(refund_id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, opened_by VARCHAR(128) NOT NULL, player VARCHAR(128) NOT NULL, status ENUM('open', 'in progress', 'approved', 'signed off', 'executed', 'denied') DEFAULT 'open', final_decision_by VARCHAR(128), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, KEY idx_player(player), KEY idx_opened_by(opened_by), KEY idx_status(status)) Engine=InnoDB;");
			sh.execute("CREATE TABLE IF NOT EXISTS " + thRefundDetail + "(detail_id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, refund_id INT UNSIGNED NOT NULL, amount INT UNSIGNED NOT NULL, amount_refunded INT UNSIGNED NOT NULL DEFAULT 0, item_id INT UNSIGNED NOT NULL, item_meta INT UNSIGNED NOT NULL, KEY idx_lookup(refund_id, detail_id)) Engine=InnoDB;");
			connCleanup();
			return connection;
		} catch (Exception e) {
			plugin.getLogger().severe("Unable to establish a connection to the DB, disabling myself as I'm useless without it");
			plugin.getServer().getPluginManager().disablePlugin(plugin);
			return null;
		}
	}
	
	private void connCleanup() {
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			public void run() {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }, 3L);
	}
	
	public boolean createRefund(String submitter, String beneficiary, Timestamp date) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("INSERT INTO " + thRefund + "(opened_by, player, created_at, updated_at) VALUES (?,?,?,?)");
		ps.setString(1, submitter);
		ps.setString(2, beneficiary);
		ps.setTimestamp(3, date);
		ps.setTimestamp(4, date);
		ps.execute();
		ps.close();
		return true;
	}
	
	public int countRefunds(String beneficiary) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT COUNT(1) FROM " + thRefund + " WHERE player = '" + beneficiary + "' AND status IN ('open', 'in progress')");
		int refundRequests = 0;
		while (rs.next()) {
			refundRequests = rs.getInt(1);
		}
		rs.close();
		return refundRequests;
	}
	
	public int getLatestRefundId(String player) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT refund_id FROM " + thRefund + " WHERE player = '" + player + "' AND status IN ('open', 'in progress')");
		int refundRequests = 0;
		while (rs.next()) {
			refundRequests = rs.getInt(1);
		}
		rs.close();
		return refundRequests;
	}
	
	public void addRefund(String player, Integer quantity, Integer itemid, short metaid, Integer refundId) throws SQLException {
		Connection conn = establishConnection();
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = ?, updated_at = NOW() WHERE refund_id = ?");
		ps.setString(1, "in progress");
		ps.setInt(2, refundId);
		ps.execute();
		ps.close();
		ps = conn.prepareStatement("INSERT INTO " + thRefundDetail + " (refund_id, amount, item_id, item_meta) VALUES (?,?,?,?)");
		ps.setInt(1, refundId);
		ps.setInt(2, quantity);
		ps.setInt(3, itemid);
		ps.setInt(4, metaid);
		ps.execute();
		ps.close();		
	}
	
	public void signRefund(Integer refundId) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = 'signed off', updated_at = NOW() WHERE refund_id = ?");
		ps.setInt(1, refundId);
		ps.execute();
		ps.close();
		return;
	}
	
	public void listPendingApprovals(Player staffmember) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT r.refund_id, r.player, r.status, r.created_at, r.opened_by, count(rd.detail_id), sum(rd.amount) FROM " + thRefund + " r INNER JOIN " + thRefundDetail + " rd ON r.refund_id = rd.refund_id WHERE r.status IN ('open', 'in progress', 'signed off') GROUP BY 1");
		staffmember.sendMessage(ChatColor.GOLD + "-----List-of-pending-refunds------");
		staffmember.sendMessage(ChatColor.GOLD + "ID , Player , OpenedBy , Unique Items , Total items , Status, Created At");
		while (rs.next()) {
			String message = "#" + rs.getInt(1);
			message = message + ", " + rs.getString(2);
			message = message + ", " + rs.getString(5);
			message = message + ", " + rs.getInt(6);
			message = message + ", " + rs.getInt(7);
			message = message + ", " + rs.getString(3);
			message = message + ", " + rs.getString(4);
			sendSummary(staffmember, message);
 		}
		staffmember.sendMessage(ChatColor.GOLD + "-----End-of-pending-refunds------");
		rs.close();
		
	}
	
	public void getRefundDetailById(Player staffmember, Integer refundId) throws SQLException {
		Connection conn = establishConnection();
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT rd.item_id, rd.item_meta, rd.amount FROM " + thRefundDetail + " rd WHERE rd.refund_id = " + refundId);
		staffmember.sendMessage(ChatColor.GOLD + "-----Details-for-ID-" + refundId + "------");
		while (rs.next()) {
			staffmember.sendMessage(ChatColor.GRAY + "item:meta: " + ChatColor.WHITE + rs.getInt(1) + ":" + rs.getInt(2) + ChatColor.GRAY + ", amount: " + ChatColor.WHITE + rs.getInt(3));
		}
		staffmember.sendMessage(ChatColor.GOLD + "------End-of-details------");
		rs.close();
		return;
	}
	
	public void denyRefundId(Integer refundId) throws SQLException {
		Connection conn = establishConnection();
		SSUtil utils = plugin.getUtil();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = 'denied' WHERE refund_id = ?");
		ps.setInt(1, refundId);
		ps.execute();
		ps.close();
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT player FROM " + thRefund + " WHERE refund_id = " + refundId);
		while (rs.next()) {
			utils.sendDeniedMessage(rs.getString(1), "denied");
		}
		return;
	}
	
	public void testExecute(Integer refundId, String player) throws SQLException {
		HashMap<String, Integer> toRefund = new HashMap<String, Integer>();
		Connection conn = establishConnection();
		SSUtil util = plugin.getUtil();
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT rd.item_id, rd.item_meta, rd.amount FROM " + thRefundDetail + " rd WHERE rd.refund_id = " + refundId);
		while (rs.next()) {
			toRefund.put(rs.getString(1) + ":" + rs.getString(2), rs.getInt(3));
		}
		rs.close();
		Player refundTo = util.findOnlinePlayerByName(player);
		if (refundTo == null)
			return;
		Iterator<String> keySetIterator = toRefund.keySet().iterator();
		boolean outofspace = false;
		while(keySetIterator.hasNext() && !outofspace){
			  String key = keySetIterator.next();
			  String[] args = key.split(":");
			  Material material = Material.matchMaterial(args[0]);
				if (material != null) {
					// We have a material, now let's see if there's room for it... todo: optimize these things
					Integer given = 0;
					while (given < toRefund.get(key)) {
						Integer invSlot = refundTo.getInventory().firstEmpty();
						if (invSlot < 0) {
							// No more empty slots, save what we've done and retry later
							util.sendMessageGG(refundTo, "You're out of inventory space. Stopping the refund - for a player this would check again after some time to see if it can continue");
							outofspace = true;
							break;
						} else {
							System.out.println("int: " + Integer.parseInt(args[0]) + " meta: " + (short) Integer.parseInt(args[1]));
							refundTo.getInventory().addItem(new ItemStack(material, 1, (short) Integer.parseInt(args[1])));
							given++;
						}
					}
					// This is where we would update the DB with what we gave the user
					//System.out.println("Gave " +given+ " of " + material);
				}
		}
	}
	
	public void executePendingRefund() throws SQLException {
		
	}
	
	public void sendSummary(Player staffmember, String message) {
		staffmember.sendMessage(ChatColor.GOLD + "-" + ChatColor.AQUA + message);
	}
}
