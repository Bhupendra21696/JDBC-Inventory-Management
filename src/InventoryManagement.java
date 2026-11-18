import java.sql.*;
import java.util.Scanner;

/*
 * SETUP (run once in MySQL):
 *   CREATE DATABASE IF NOT EXISTS retail_db;
 *   USE retail_db;
 *   CREATE TABLE IF NOT EXISTS Product (
 *       product_id   INT PRIMARY KEY,
 *       product_name VARCHAR(100),
 *       category     VARCHAR(50),
 *       quantity     INT,
 *       price        DOUBLE
 *   );
 *
 * Add MySQL Connector/J to the classpath, e.g.:
 *   javac InventoryManagement.java
 *   java -cp .:mysql-connector-j-8.x.jar InventoryManagement
 */
public class InventoryManagement {

    private static final String URL  = "jdbc:mysql://localhost:3306/retail_db";
    private static final String USER = "root";        // change as needed
    private static final String PASS = "Prince@90"; // change as needed

    private static Connection con;
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            createDatabase();
            con = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("Connected to retail_db.");
            createTable();

            int choice;
            do {
                System.out.println("\n===== INVENTORY MANAGEMENT SYSTEM =====");
                System.out.println("1. Insert product records");
                System.out.println("2. Show low-stock products by category (qty < 20)");
                System.out.println("3. Update product stock");
                System.out.println("4. Search and modify product price");
                System.out.println("5. Total inventory value & delete zero-stock product");
                System.out.println("6. Exit");
                System.out.print("Enter choice: ");
                choice = readInt();

                switch (choice) {
                    case 1: insertProducts();    break;
                    case 2: lowStockByCategory(); break;
                    case 3: updateStock();       break;
                    case 4: modifyPrice();       break;
                    case 5: valueAndDelete();    break;
                    case 6: System.out.println("Goodbye!"); break;
                    default: System.out.println("Invalid choice. Try again.");
                }
            } while (choice != 6);

        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        } finally {
            try { if (con != null) con.close(); } catch (SQLException ignored) { }
            sc.close();
        }
    }

    // ---------- Setup: create database and table if missing ----------
    private static void createDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/", USER, PASS);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS retail_db");
        }
    }

    private static void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS Product ("
                   + "product_id INT PRIMARY KEY, "
                   + "product_name VARCHAR(100), "
                   + "category VARCHAR(50), "
                   + "quantity INT, "
                   + "price DOUBLE)";
        try (Statement st = con.createStatement()) {
            st.executeUpdate(sql);
            System.out.println("Table 'Product' is ready.");
        } catch (SQLException e) {
            System.out.println("Error creating table: " + e.getMessage());
        }
    }

    // ---------- (a) Insert Product Records ----------
    private static void insertProducts() {
        String sql = "INSERT INTO Product (product_id, product_name, category, quantity, price) "
                   + "VALUES (?, ?, ?, ?, ?)";
        System.out.print("How many products to insert (at least 3)? ");
        int n = readInt();
        if (n < 3) {
            System.out.println("Please insert at least three records.");
            return;
        }

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= n; i++) {
                System.out.println("--- Product " + i + " ---");
                System.out.print("Product ID: ");
                int id = readInt();
                System.out.print("Name: ");
                String name = sc.nextLine().trim();
                System.out.print("Category: ");
                String cat = sc.nextLine().trim();
                System.out.print("Quantity: ");
                int qty = readInt();
                System.out.print("Price: ");
                double price = readDouble();

                ps.setInt(1, id);
                ps.setString(2, name);
                ps.setString(3, cat);
                ps.setInt(4, qty);
                ps.setDouble(5, price);

                try {
                    ps.executeUpdate();
                    System.out.println("Product " + id + " inserted successfully.");
                } catch (SQLIntegrityConstraintViolationException dup) {
                    System.out.println("Error: Product ID " + id + " already exists. Record skipped.");
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1062) {
                        System.out.println("Error: Duplicate Product ID " + id + ". Record skipped.");
                    } else {
                        System.out.println("Insert failed: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- (b) Conditional Retrieval and Sorting ----------
    private static void lowStockByCategory() {
        System.out.print("Enter category: ");
        String cat = sc.nextLine().trim();
        String sql = "SELECT product_id, product_name, category, quantity, price "
                   + "FROM Product WHERE category = ? AND quantity < 20 ORDER BY price DESC";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cat);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                System.out.printf("%-6s %-25s %-15s %-8s %-10s%n",
                        "ID", "Name", "Category", "Qty", "Price");
                while (rs.next()) {
                    found = true;
                    System.out.printf("%-6d %-25s %-15s %-8d %-10.2f%n",
                            rs.getInt("product_id"), rs.getString("product_name"),
                            rs.getString("category"), rs.getInt("quantity"),
                            rs.getDouble("price"));
                }
                if (!found) {
                    System.out.println("No products in category '" + cat + "' with quantity less than 20.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- (c) Update Product Stock ----------
    private static void updateStock() {
        System.out.print("Enter product ID: ");
        int id = readInt();
        System.out.print("Enter quantity to add: ");
        int add = readInt();

        String sql = "UPDATE Product SET quantity = quantity + ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, add);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                System.out.println("Stock updated successfully for product " + id + ".");
            } else {
                System.out.println("Product with ID " + id + " does not exist.");
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- (d) Search and Modify Product Details ----------
    private static void modifyPrice() {
        System.out.print("Enter product ID: ");
        int id = readInt();
        System.out.print("Enter new price: ");
        double newPrice = readDouble();

        String select = "SELECT * FROM Product WHERE product_id = ?";
        String update = "UPDATE Product SET price = ? WHERE product_id = ?";

        try (PreparedStatement sel = con.prepareStatement(select)) {
            sel.setInt(1, id);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " does not exist.");
                    return;
                }
                System.out.println("Current details:");
                printProduct(rs);
            }

            if (newPrice <= 0) {
                System.out.println("Invalid price. Price must be greater than zero. No update made.");
                return;
            }

            try (PreparedStatement upd = con.prepareStatement(update)) {
                upd.setDouble(1, newPrice);
                upd.setInt(2, id);
                upd.executeUpdate();
            }

            try (PreparedStatement sel2 = con.prepareStatement(select)) {
                sel2.setInt(1, id);
                try (ResultSet rs = sel2.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("Updated details:");
                        printProduct(rs);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- (e) Aggregate Query and Conditional Deletion ----------
    private static void valueAndDelete() {
        String sumSql = "SELECT SUM(quantity * price) AS total_value FROM Product";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sumSql)) {
            if (rs.next()) {
                System.out.printf("Total inventory value: %.2f%n", rs.getDouble("total_value"));
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        System.out.print("Enter product ID to delete: ");
        int id = readInt();

        String check = "SELECT quantity FROM Product WHERE product_id = ?";
        String del   = "DELETE FROM Product WHERE product_id = ? AND quantity = 0";

        try (PreparedStatement ps = con.prepareStatement(check)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("Product with ID " + id + " does not exist.");
                    return;
                }
                int qty = rs.getInt("quantity");
                if (qty != 0) {
                    System.out.println("Cannot delete product " + id + ": quantity is " + qty + " (must be 0).");
                    return;
                }
            }
            try (PreparedStatement dp = con.prepareStatement(del)) {
                dp.setInt(1, id);
                if (dp.executeUpdate() > 0) {
                    System.out.println("Product " + id + " deleted successfully.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // ---------- Helpers ----------
    private static void printProduct(ResultSet rs) throws SQLException {
        System.out.printf("ID: %d | Name: %s | Category: %s | Qty: %d | Price: %.2f%n",
                rs.getInt("product_id"), rs.getString("product_name"),
                rs.getString("category"), rs.getInt("quantity"), rs.getDouble("price"));
    }

    private static int readInt() {
        while (true) {
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Invalid number, try again: ");
            }
        }
    }

    private static double readDouble() {
        while (true) {
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Invalid number, try again: ");
            }
        }
    }
}
