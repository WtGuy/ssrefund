package com.serendipitymc.refund.managers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

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
    private SSUtil util;
    
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
			sh.execute("CREATE TABLE IF NOT EXISTS " + thRefund + "(refund_id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, opened_by VARCHAR(128) NOT NULL, player VARCHAR(128) NOT NULL, status ENUM('open', 'in progress', 'approved', 'signed off', 'executed', 'denied') DEFAULT 'open', final_decision_by VARCHAR(128), comment VARCHAR(256), servername VARCHAR(128), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, KEY idx_player(player), KEY idx_opened_by(opened_by), KEY idx_status(status), KEY idx_server(servername)) Engine=InnoDB;");
			sh.execute("CREATE TABLE IF NOT EXISTS " + thRefundDetail + "(detail_id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, refund_id INT UNSIGNED NOT NULL, amount INT UNSIGNED NOT NULL, amount_refunded INT UNSIGNED NOT NULL DEFAULT 0, item_id INT UNSIGNED NOT NULL, item_meta INT UNSIGNED NOT NULL, KEY idx_lookup(refund_id, detail_id), UNIQUE KEY (refund_id, item_id, item_meta)) Engine=InnoDB;");
			connCleanup();
			return connection;
		} catch (Exception e) {
			plugin.getLogger().severe("Unable to establish a connection to the DB, disabling myself as I'm useless without it");
			Bukkit.getScheduler().cancelTasks(plugin);
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
	
	public boolean createRefund(String submitter, String beneficiary, Timestamp date, String comment, String servername) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("INSERT INTO " + thRefund + "(opened_by, player, created_at, updated_at, comment, servername) VALUES (?,?,?,?,?,?)");
		ps.setString(1, submitter);
		ps.setString(2, beneficiary);
		ps.setTimestamp(3, date);
		ps.setTimestamp(4, date);
		ps.setString(5, comment);
		ps.setString(6, servername);
		ps.execute();
		ps.close();
		return true;
	}
	
	public int countRefunds(String beneficiary, String servername) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT COUNT(1) FROM " + thRefund + " WHERE player = '" + beneficiary + "' AND status IN ('open', 'in progress') AND servername = '" + servername + "'");
		int refundRequests = 0;
		while (rs.next()) {
			refundRequests = rs.getInt(1);
		}
		rs.close();
		return refundRequests;
	}
	
	public int getLatestRefundId(String player, String servername) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT refund_id FROM " + thRefund + " WHERE player = '" + player + "' AND status IN ('open', 'in progress') AND servername = '" + servername + "'");
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
		ps = conn.prepareStatement("INSERT INTO " + thRefundDetail + " (refund_id, amount, item_id, item_meta) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE amount = ?");
		ps.setInt(1, refundId);
		ps.setInt(2, quantity);
		ps.setInt(3, itemid);
		ps.setInt(4, metaid);
		ps.setInt(5, quantity);
		ps.execute();
		ps.close();		
	}
	
	public void signRefund(Integer refundId) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = 'signed off', updated_at = NOW() WHERE refund_id = ?");
		ps.setInt(1, refundId);
		ps.execute();
		ps.close();
		return;
	}
	
	public Integer getItemCount(Integer refundId) throws SQLException {
		Connection conn = establishConnection();
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT SUM(amount) FROM " + thRefundDetail + " WHERE refund_id = " + refundId);
		int refundRequests = 0;
		while (rs.next()) {
			refundRequests = rs.getInt(1);
		}
		rs.close();
		return refundRequests;
	}
	
	public void listPendingApprovals(Player staffmember, int page) throws SQLException {
		Connection conn = establishConnection();
		int nmbr = page * 10;
		int lowNumber = nmbr - 10;
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT r.refund_id, r.player, r.status, r.created_at, r.opened_by, count(rd.detail_id), sum(rd.amount), r.servername FROM " + thRefund + " r LEFT OUTER JOIN " + thRefundDetail + " rd ON r.refund_id = rd.refund_id WHERE r.status IN ('open', 'in progress', 'signed off') GROUP BY 1 ORDER BY r.refund_id ASC LIMIT "+ lowNumber + ", 10");
		staffmember.sendMessage(ChatColor.GOLD + "-----List-of-pending-refunds------");
		staffmember.sendMessage(ChatColor.GOLD + "ID , Player , OpenedBy , Unique Items , Total items , Status, Created At, Server");
		
		while (rs.next()) {
			String finalMessage = "";
			finalMessage = finalMessage + ChatColor.GOLD + "#" + rs.getInt(1) + " ";
			finalMessage = finalMessage + ChatColor.DARK_GREEN + rs.getString(4) + " ";
			finalMessage = finalMessage + "[" + rs.getInt(6) + "]" + " ";
			
			String statusVar = rs.getString(3);
			if (statusVar.equals("signed off"))
				finalMessage = finalMessage + ChatColor.GREEN + rs.getString(2) + " ";
			else
				finalMessage = finalMessage + ChatColor.RED + rs.getString(2) + " ";
				
			finalMessage = finalMessage + ChatColor.GRAY + rs.getString(8) + " ";
			finalMessage = finalMessage + ChatColor.RED + "[" + rs.getString(3) + "]";
      
			sendSummary(staffmember, finalMessage);
 		}
		staffmember.sendMessage(ChatColor.GOLD + "-----End-of-pending-refunds------");
		staffmember.sendMessage(ChatColor.GOLD + "Do /refund list <page> to see more");
		rs.close();
	}
	
	public void showPlayerHistoryBrief(Player staffmember, String player) throws SQLException {
		Connection conn = establishConnection();
		util = plugin.getUtil();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT r.refund_id, r.player, r.status, r.created_at, r.opened_by, count(rd.detail_id), sum(rd.amount), r.servername FROM " + thRefund + " r LEFT OUTER JOIN " + thRefundDetail + " rd ON r.refund_id = rd.refund_id WHERE r.player = '"+player+"' ORDER BY r.refund_id DESC");
		util.sendMessageGG(staffmember, "----Refund history for " + player + "----");
		while (rs.next()) {
			String message = "#" + rs.getInt(1);
			message = message + ", " + rs.getString(2);
			message = message + ", " + rs.getString(5);
			message = message + ", " + rs.getInt(6);
			message = message + ", " + rs.getInt(7);
			message = message + ", " + rs.getString(3);
			message = message + ", " + rs.getString(4);
			message = message + ", " + rs.getString(8);
			if (rs.getInt(1) != 0)
				sendSummary(staffmember, message);
		}
		rs.close();
	}
	
	public void getRefundDetailById(Player staffmember, Integer refundId, int page) throws SQLException {
		Connection conn = establishConnection();
		util = plugin.getUtil();
		int number = page * 10;
		int lowNumber = number - 10;
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT rd.item_id, rd.item_meta, rd.amount, rd.amount_refunded FROM " + thRefundDetail + " rd WHERE rd.refund_id = " + refundId + " ORDER BY rd.item_id DESC LIMIT " + lowNumber + ", 10");
		Statement sh2 = conn.createStatement();
		ResultSet rs2 = sh2.executeQuery("SELECT comment, status, opened_by, player FROM " + thRefund + " WHERE refund_id = " + refundId);
		staffmember.sendMessage(ChatColor.GOLD + "-----Details-for-ID-" + refundId + "------");
		while (rs2.next()) {
			staffmember.sendMessage(ChatColor.DARK_GREEN + "Status: " + ChatColor.GRAY + rs2.getString(2));
			staffmember.sendMessage(ChatColor.DARK_GREEN + "Opening comment: " + ChatColor.GRAY + rs2.getString(1));
			staffmember.sendMessage(ChatColor.DARK_GREEN + "Opened by: " + ChatColor.GRAY + rs2.getString(3));
			staffmember.sendMessage(ChatColor.DARK_GREEN + "Player: " + ChatColor.GRAY + rs2.getString(4));
		}
		rs2.close();
		while (rs.next()) {
			staffmember.sendMessage(ChatColor.GRAY + "item:meta: " + ChatColor.WHITE + rs.getInt(1) + ":" + rs.getInt(2) + ChatColor.GRAY + ", amount: " + ChatColor.WHITE + rs.getInt(3) + ChatColor.GRAY + ", refunded: " + ChatColor.WHITE + rs.getInt(4));
		}
		staffmember.sendMessage(ChatColor.GOLD + "------End-of-details------");
		util.sendMessageGG(staffmember, "use /refund detail <id> <page> to see more");
		rs.close();
		return;
	}
	
	public void denyRefundId(Integer refundId, String staffmember) throws SQLException {
		Connection conn = establishConnection();
		SSUtil utils = plugin.getUtil();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = 'denied', final_decision_by = ?, updated_at = NOW() WHERE refund_id = ?");
		ps.setString(1, staffmember);
		ps.setInt(2, refundId);
		ps.execute();
		ps.close();
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT player FROM " + thRefund + " WHERE refund_id = " + refundId);
		while (rs.next()) {
			utils.sendDeniedMessage(rs.getString(1), "denied");
		}
		return;
	}
	
	public void approveRefundId(Integer refundId, String staffmember) throws SQLException {
		Connection conn = establishConnection();
		SSUtil utils = plugin.getUtil();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = 'approved', final_decision_by = ?, updated_at = NOW() WHERE refund_id = ? AND status NOT IN ('executed', 'denied')");
		ps.setString(1, staffmember);
		ps.setInt(2, refundId);
		ps.execute();
		ps.close();
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT player FROM " + thRefund + " WHERE refund_id = " + refundId);
		while (rs.next()) {
			utils.sendDeniedMessage(rs.getString(1), "approved");
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
							refundTo.getInventory().addItem(new ItemStack(material, 1, (short) Integer.parseInt(args[1])));
							given++;
						}
					}
					// This is where we would update the DB with what we gave the user
					//System.out.println("Gave " +given+ " of " + material);
				}
		}
	}
	
	public void executePendingRefund(String servername) throws SQLException {
		Connection conn = establishConnection();
		HashMap<String, Integer> toRefund = new HashMap<String, Integer>();
		HashMap<String, Integer> refunds = new HashMap<String, Integer>();
		List<Player> onlinePlayers = new ArrayList<Player>();
		SSUtil util = plugin.getUtil();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		String thRefundDetail = plugin.getConfig().getString("mysql.tables.items");
		Statement sh = conn.createStatement();
		ResultSet rs = sh.executeQuery("SELECT r.refund_id, r.player FROM " + thRefund + " r WHERE r.status = 'approved' AND r.servername = '" + servername + "'");
		while (rs.next()) {
			refunds.put(rs.getString(2), rs.getInt(1));
		}
		rs.close();
		onlinePlayers = util.getOnlinePlayers(refunds);
		
		for (Player player : onlinePlayers) {
			sh = conn.createStatement();
			rs = sh.executeQuery("SELECT rd.item_id, rd.item_meta, (rd.amount - rd.amount_refunded) AS remaining FROM " + thRefundDetail + " rd WHERE rd.refund_id = " + refunds.get(player.getName().toLowerCase()) + " AND (rd.amount - rd.amount_refunded) > 0");
			while (rs.next()) {
				toRefund.put(rs.getString(1) + ":" + rs.getString(2), rs.getInt(3));
			}
			rs.close();
			Iterator<String> keySetIterator = toRefund.keySet().iterator();
			boolean outofspace = false;
			while(keySetIterator.hasNext() && !outofspace){
				  String key = keySetIterator.next();
				  String[] args = key.split(":");
				  Material material = Material.matchMaterial(args[0]);
					if (material != null) {
						// We have a material, now let's see if there's room for it... todo: optimize these things
						Integer given = 0;
						while (given < toRefund.get(key) && !outofspace) {
							Integer invSlot = player.getInventory().firstEmpty();
							if (invSlot < 0) {
								// No more empty slots, save what we've done and retry later
								util.sendMessageGG(player, "You're out of inventory space. Pausing the refund. The system will automatically keep retrying until refund is complete");
								outofspace = true;
								break;
							} else {
								player.getInventory().addItem(new ItemStack(material, 1, (short) Integer.parseInt(args[1])));
								given++;
							}
						}
						// debug
						//util.sendMessageGG(player, "before update");
						PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefundDetail + " SET amount_refunded = amount_refunded + ? WHERE refund_id = ? AND item_id = ? AND item_meta = ?");
						ps.setInt(1, given);
						ps.setInt(2, refunds.get(player.getName().toLowerCase()));
						ps.setInt(3, Integer.parseInt(args[0]));
						ps.setInt(4, Integer.parseInt(args[1]));
						ps.execute();
						ps.close();
					}
			}
			if (!outofspace) {
				PreparedStatement ps = conn.prepareStatement("UPDATE " + thRefund + " SET status = 'executed', updated_at = NOW() WHERE refund_id = ?");
				ps.setInt(1, refunds.get(player.getName().toLowerCase()));
				ps.execute();
				ps.close();
			}
		}
	}
	
	public void sendSummary(Player staffmember, String message) {
		staffmember.sendMessage(ChatColor.GOLD + "-" + ChatColor.AQUA + message);
	}

	public int pendingRefundsForPlayer(String player, String server) throws SQLException {
		Connection conn = establishConnection();
		String thRefund = plugin.getConfig().getString("mysql.tables.refunds");
		Statement sh = conn.createStatement();
		int returnInt = 0;
		ResultSet rs = sh.executeQuery("SELECT COUNT(1) FROM " + thRefund + " WHERE player ='" + player + "' AND status IN ('open', 'in progress') AND servername = '" +server+"'");
		while (rs.next()) {
			if (rs.getInt(1) > returnInt)
				returnInt = rs.getInt(1);
		}
		conn.close();
		return returnInt;
	}
}
