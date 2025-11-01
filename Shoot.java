import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;

public class Shoot extends JPanel implements KeyListener, ActionListener {

    private static final int TIMER_ID = 1; //定时器ID
    private static final int PLAYER_RADIUS = 15; // 玩家角色半径
    private static final int MAX_OBSTACLE_COUNT = 20; // 最大障碍物数量
    private static final int INIT_OBSTACLE_COUNT = 10; // 初始障碍物数量
    private static final int OBSTACLE_WIDTH = 30; // 障碍物宽度
    private static final int OBSTACLE_HEIGHT = 20; // 障碍物高度
    private static final int PLAYER_SPEED = 5; // 玩家移动速度
    private static final Color PLAYER_COLOR = Color.RED; // 玩家角色颜色
    private static final int BULLET_RADIUS = 6;
    private static final int BULLET_SPEED = 12;
    private static final int ENEMY_BULLET_RADIUS = 6;
    private static final int ENEMY_BULLET_SPEED = 8;
    private static final int MAX_ENEMY_BULLETS = 64;
    private static final String HIGH_SCORE_FILE = "highscore.dat";

    private static final int MAX_PLAYER_BULLETS = 3;

    private Timer timer;
    private int highScore = 0;

    private Player player = new Player();

    private Obstacle[] obstacles = new Obstacle[MAX_OBSTACLE_COUNT];
    private int obstacleCount = INIT_OBSTACLE_COUNT;

    private Bullet[] playerBullets = new Bullet[MAX_PLAYER_BULLETS];
    private int[] obstacleHitCount = new int[MAX_OBSTACLE_COUNT];

    private EnemyBullet[] enemyBullets = new EnemyBullet[MAX_ENEMY_BULLETS];

    private int playerHitCount = 0;
    private int enemyBulletFireCounter = 0;

    private boolean gameOver = false;
    private int score = 0;

    private boolean protectionOn = false;
    private int protectionFrame = 0;

    // 将 lastProtectionScore 提升为类字段，避免在方法内使用 static 局部变量
    private int lastProtectionScore = 0;

    public Shoot() {
        setPreferredSize(new Dimension(800, 600));
        setFocusable(true);
        addKeyListener(this);

        // 初始化障碍物
        for (int i = 0; i < MAX_OBSTACLE_COUNT; i++) {
            obstacles[i] = new Obstacle();
        }
        for (int i = 0; i < obstacleCount; i++) {
            obstacles[i].initActive();
        }

        // 初始化玩家子弹
        for (int i = 0; i < MAX_PLAYER_BULLETS; i++) {
            playerBullets[i] = new Bullet();
        }

        // 初始化敌方子弹
        for (int i = 0; i < MAX_ENEMY_BULLETS; i++) {
            enemyBullets[i] = new EnemyBullet();
        }

        // 载入最高分
        loadHighScore();

        // 设置定时器（使用 javax\.swing\.Timer）
        timer = new Timer(30, this);
        timer.start();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // 绘制背景
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // 绘制分数
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 24));
        g2d.drawString("Score: " + score + "    Highest Score: " + highScore, 10, 20);

        // 绘制玩家角色
        g2d.setColor(PLAYER_COLOR);
        g2d.fillOval(player.x - PLAYER_RADIUS, player.y - PLAYER_RADIUS, PLAYER_RADIUS * 2, PLAYER_RADIUS * 2);

        // 绘制障碍物
        for (int i = 0; i < obstacleCount; i++) {
            if (obstacles[i].active) {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(obstacles[i].x, obstacles[i].y, OBSTACLE_WIDTH, OBSTACLE_HEIGHT);
            }
        }

        // 绘制玩家子弹
        for (int b = 0; b < MAX_PLAYER_BULLETS; b++) {
            if (playerBullets[b].active) {
                g2d.setColor(Color.BLACK);
                g2d.fillOval(playerBullets[b].x - BULLET_RADIUS, playerBullets[b].y - BULLET_RADIUS, BULLET_RADIUS * 2, BULLET_RADIUS * 2);
            }
        }

        // 绘制敌方子弹
        for (int i = 0; i < MAX_ENEMY_BULLETS; i++) {
            if (enemyBullets[i].active) {
                g2d.setColor(Color.BLACK);
                g2d.fillOval(enemyBullets[i].x - ENEMY_BULLET_RADIUS, enemyBullets[i].y - ENEMY_BULLET_RADIUS, ENEMY_BULLET_RADIUS * 2, ENEMY_BULLET_RADIUS * 2);
            }
        }

        // 绘制保护盾
        if (protectionOn) {
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
            g2d.drawArc(player.x - PLAYER_RADIUS - 8, player.y - PLAYER_RADIUS - 8, (PLAYER_RADIUS + 8) * 2, (PLAYER_RADIUS + 8) * 2, 0, 360);
            g2d.drawString("Protection On", 300, 20);
        }

        // 游戏结束信息
        if (gameOver) {
            g2d.setColor(Color.RED);
            g2d.setFont(new Font("Arial", Font.BOLD, 36));
            g2d.drawString("Game is over! Points: " + score, getWidth() / 4, getHeight() / 3);
            g2d.drawString("Press ESC to quit or press the menu to restart", getWidth() / 4, getHeight() / 3 + 40);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameOver) {
            updateGame();
        }
        repaint();
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // 忽略
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int speed = PLAYER_SPEED;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                player.x -= speed;
                break;
            case KeyEvent.VK_RIGHT:
                player.x += speed;
                break;
            case KeyEvent.VK_UP:
                player.y -= speed;
                break;
            case KeyEvent.VK_DOWN:
                player.y += speed;
                break;
            case KeyEvent.VK_SPACE:
                if (score >= 10000) break; // 分数超过10000时不允许发射

                // 统计可用子弹槽
                int available = 0;
                for (int i = 0; i < MAX_PLAYER_BULLETS; i++) {
                    if (!playerBullets[i].active) available++;
                }

                if (available == 0) break;

                // 收集最近的障碍物（使用 java\.util\.List 完全限定，避免 java\.awt\.List 歧义）
                java.util.List<TargetInfo> targets = new ArrayList<>();
                for (int i = 0; i < obstacleCount; i++) {
                    if (!obstacles[i].active) continue;

                    int ox = obstacles[i].x + OBSTACLE_WIDTH / 2;
                    int oy = obstacles[i].y + OBSTACLE_HEIGHT / 2;
                    int dx = ox - player.x;
                    int dy = oy - player.y;
                    int dist = dx * dx + dy * dy;
                    targets.add(new TargetInfo(i, dist));
                }
                targets.sort(Comparator.comparingInt(target -> target.dist));

                int bulletCount = (score >= 5000) ? 3 : 1;
                int fired = 0;
                for (int t = 0; t < targets.size() && fired < bulletCount; t++) {
                    int i = targets.get(t).idx;
                    int ox = obstacles[i].x + OBSTACLE_WIDTH / 2;
                    int oy = obstacles[i].y + OBSTACLE_HEIGHT / 2;
                    double vx = ox - player.x;
                    double vy = oy - player.y;
                    double len = Math.sqrt(vx * vx + vy * vy);
                    if (len > 0.1) {
                        // 找到空闲子弹槽
                        for (int b = 0; b < MAX_PLAYER_BULLETS; b++) {
                            if (!playerBullets[b].active) {
                                playerBullets[b].x = player.x;
                                playerBullets[b].y = player.y;
                                playerBullets[b].dx = vx / len;
                                playerBullets[b].dy = vy / len;
                                playerBullets[b].active = true;
                                fired++;
                                break;
                            }
                        }
                    }
                }
                break;
            case KeyEvent.VK_ESCAPE:
                timer.stop();
                gameOver = true;
                break;
            case KeyEvent.VK_ENTER:
                if (gameOver) {
                    initGame();
                    gameOver = false;
                    score = 0;
                    timer.start();
                }
                break;
        }

        // 确保玩家角色不出界
        if (player.x < PLAYER_RADIUS) player.x = PLAYER_RADIUS;
        if (player.x > getWidth() - PLAYER_RADIUS) player.x = getWidth() - PLAYER_RADIUS;
        if (player.y < PLAYER_RADIUS) player.y = PLAYER_RADIUS;
        if (player.y > getHeight() - PLAYER_RADIUS) player.y = getHeight() - PLAYER_RADIUS;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // 忽略
    }

    private void initGame() {
        player.x = 400;
        player.y = 300;
        player.active = true;

        obstacleCount = INIT_OBSTACLE_COUNT;
        for (int i = 0; i < obstacleCount; i++) {
            obstacles[i].initActive();
        }
        for (int i = obstacleCount; i < MAX_OBSTACLE_COUNT; i++) {
            obstacles[i].active = false;
        }

        gameOver = false;
        score = 0;
        protectionOn = false;
        protectionFrame = 0;

        for (int i = 0; i < MAX_PLAYER_BULLETS; i++) playerBullets[i].active = false;
        for (int i = 0; i < MAX_OBSTACLE_COUNT; i++) obstacleHitCount[i] = 0;
    }

    private void updateGame() {
        int specialEnemyBulletCounter = 0;

        // 新增逻辑：障碍物数量小于2时补充到10个
        int activeCount = 0;
        for (int i = 0; i < obstacleCount; i++) {
            if (obstacles[i].active) activeCount++;
        }
        if (activeCount < 2 && obstacleCount < 10) {
            for (int i = obstacleCount; i < 10; i++) {
                obstacles[i].initActive();
            }
            obstacleCount = 10;
        }

        // 分数超过2000及其后每500分增加障碍物
        if (score >= 2000 && (score - 2000) % 500 == 0 && obstacleCount < MAX_OBSTACLE_COUNT) {
            for (int i = obstacleCount; i < MAX_OBSTACLE_COUNT; i++) {
                obstacles[i].initActive();
            }
            obstacleCount = MAX_OBSTACLE_COUNT;
        }

        // 在障碍物移动前，动态调整速度
        int speedUp = 1 + score / 1000; // 每1000分提升一次
        for (int i = 0; i < obstacleCount; i++) {
            if (obstacles[i].active) {
                // 限制最大速度
                obstacles[i].speedX = Math.min(Math.max(obstacles[i].speedX, -speedUp * 3), speedUp * 3);
                obstacles[i].speedY = Math.min(Math.max(obstacles[i].speedY, -speedUp * 3), speedUp * 3);
            }
        }

        // 偶尔让障碍物随机变向
        if (Math.random() * 1000 < score / 100) { // 分数越高概率越大
            for (int i = 0; i < obstacleCount; i++) {
                if (obstacles[i].active && Math.random() * 10 < 1) {
                    obstacles[i].initSpeed();
                }
            }
        }

        // 更新障碍物位置
        for (int i = 0; i < obstacleCount; i++) {
            if (obstacles[i].active) {
                obstacles[i].x += obstacles[i].speedX;
                obstacles[i].y += obstacles[i].speedY;

                // 碰到边界时反弹
                if (obstacles[i].x < 0 || obstacles[i].x + OBSTACLE_WIDTH > getWidth()) {
                    obstacles[i].speedX = -obstacles[i].speedX;
                }
                if (obstacles[i].y < 0 || obstacles[i].y + OBSTACLE_HEIGHT > getHeight()) {
                    obstacles[i].speedY = -obstacles[i].speedY;
                }

                // 检查碰撞
                if (checkCollision(obstacles[i])) {
                    if (!protectionOn) {
                        gameOver = true;
                        break; // 游戏结束
                    }
                    // 有护盾时不Game Over，直接跳过
                }
            }
        }

        // 更新玩家子弹位置
        int bulletSpeed = (score >= 5000) ? (BULLET_SPEED * 2) : BULLET_SPEED;
        for (int b = 0; b < MAX_PLAYER_BULLETS; b++) {
            if (!playerBullets[b].active) continue;
            playerBullets[b].x += playerBullets[b].dx * bulletSpeed;
            playerBullets[b].y += playerBullets[b].dy * bulletSpeed;

            // 出界
            if (playerBullets[b].x < 0 || playerBullets[b].x > getWidth() ||
                    playerBullets[b].y < 0 || playerBullets[b].y > getHeight()) {
                playerBullets[b].active = false;
                continue;
            }

            // 检查与障碍物碰撞
            for (int i = 0; i < obstacleCount; i++) {
                if (!obstacles[i].active) continue;
                int ox = obstacles[i].x + OBSTACLE_WIDTH / 2;
                int oy = obstacles[i].y + OBSTACLE_HEIGHT / 2;
                int dx = playerBullets[b].x - ox;
                int dy = playerBullets[b].y - oy;
                int rx = OBSTACLE_WIDTH / 2 + BULLET_RADIUS;
                int ry = OBSTACLE_HEIGHT / 2 + BULLET_RADIUS;
                if (Math.abs(dx) <= rx && Math.abs(dy) <= ry) {
                    // 命中
                    obstacleHitCount[i]++;
                    playerBullets[b].active = false;
                    if (obstacleHitCount[i] >= 2) {
                        obstacles[i].active = false;
                        obstacleHitCount[i] = 0;
                    }
                    break;
                }
            }
        }

        // 玩家子弹移动后，障碍物被击中处理后添加
        int aliveObs = 0;
        for (int i = 0; i < obstacleCount; i++) {
            if (obstacles[i].active) aliveObs++;
        }
        if (aliveObs == 0) {
            obstacleCount = MAX_OBSTACLE_COUNT;
            for (int i = 0; i < MAX_OBSTACLE_COUNT; i++) {
                obstacles[i].initActive();
                obstacleHitCount[i] = 0;
            }
        }

        // 敌方子弹发射频率控制（每500帧发射一次）
        enemyBulletFireCounter++;
        if (enemyBulletFireCounter >= 500) {
            enemyBulletFireCounter = 0;
            int firedCount = 0; // 已发射障碍物计数
            for (int i = 0; i < obstacleCount; i++) {
                if (!obstacles[i].active) continue;
                if (firedCount >= 2) break; // 只允许2个障碍物发射
                // 找到空闲子弹槽
                for (int j = 0; j < MAX_ENEMY_BULLETS; j++) {
                    if (!enemyBullets[j].active) {
                        enemyBullets[j].init(obstacles[i].x + OBSTACLE_WIDTH / 2,
                                obstacles[i].y + OBSTACLE_HEIGHT / 2, player.x, player.y);
                        break;
                    }
                }
                firedCount++; // 增加已发射障碍物计数
            }
        }

        // 敌方子弹移动
        for (int i = 0; i < MAX_ENEMY_BULLETS; i++) {
            if (!enemyBullets[i].active) continue;
            enemyBullets[i].x += enemyBullets[i].dx * ENEMY_BULLET_SPEED;
            enemyBullets[i].y += enemyBullets[i].dy * ENEMY_BULLET_SPEED;

            // 出界则消失
            if (enemyBullets[i].x < 0 || enemyBullets[i].x > getWidth() ||
                    enemyBullets[i].y < 0 || enemyBullets[i].y > getHeight()) {
                enemyBullets[i].active = false;
                continue;
            }

            // 击中玩家
            int distX = Math.abs(enemyBullets[i].x - player.x);
            int distY = Math.abs(enemyBullets[i].y - player.y);
            if (distX * distX + distY * distY <= PLAYER_RADIUS * PLAYER_RADIUS) {
                enemyBullets[i].active = false;
                if (!protectionOn) {
                    playerHitCount++;
                    if (playerHitCount >= 20) {
                        gameOver = true;
                        break;
                    }
                }
            }
        }

        // 增加分数
        score++;
        if (score > highScore) {
            highScore = score; // 更新最高分
            saveHighScore();
        }

        // 在score++后使用类字段 lastProtectionScore
        if (score / 500 > lastProtectionScore / 500) {
            protectionOn = true;
            protectionFrame = 200;
        }
        lastProtectionScore = score;

        // 保护盾帧数递减
        if (protectionOn) {
            protectionFrame--;
            if (protectionFrame <= 0) {
                protectionOn = false;
                protectionFrame = 0;
            }
        }

        // 15000分后每400帧有4个障碍物向玩家发射子弹
        if (score >= 15000) {
            specialEnemyBulletCounter++;
            if (specialEnemyBulletCounter >= 400) {
                specialEnemyBulletCounter = 0;
                int firedCount = 0;
                for (int i = 0; i < obstacleCount; i++) {
                    if (!obstacles[i].active) continue;
                    if (firedCount >= 4) break;
                    for (int j = 0; j < MAX_ENEMY_BULLETS; j++) {
                        if (!enemyBullets[j].active) {
                            enemyBullets[j].init(obstacles[i].x + OBSTACLE_WIDTH / 2,
                                    obstacles[i].y + OBSTACLE_HEIGHT / 2, player.x, player.y);
                            break;
                        }
                    }
                    firedCount++;
                }
            }
        } else {
            specialEnemyBulletCounter = 0; // 分数不到15000时计数器归零
        }
    }

    private boolean checkCollision(Obstacle obs) {
        int distX = Math.abs(player.x - (obs.x + obs.width / 2));
        int distY = Math.abs(player.y - (obs.y + obs.height / 2));

        if (distX > (obs.width / 2 + PLAYER_RADIUS) ||
                distY > (obs.height / 2 + PLAYER_RADIUS)) {
            return false; // 没有碰撞
        }

        if (distX <= (obs.width / 2) || distY <= (obs.height / 2)) {
            return true; // 碰撞
        }

        int dx = distX - obs.width / 2;
        int dy = distY - obs.height / 2;
        return (dx * dx + dy * dy <= (PLAYER_RADIUS * PLAYER_RADIUS));
    }

    private void loadHighScore() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(HIGH_SCORE_FILE))) {
            highScore = ois.readInt();
        } catch (IOException e) {
            System.err.println("Failed to load high score file: " + HIGH_SCORE_FILE);
        }
    }

    private void saveHighScore() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(HIGH_SCORE_FILE))) {
            oos.writeInt(highScore);
        } catch (IOException e) {
            System.err.println("Failed to save high score file: " + HIGH_SCORE_FILE);
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Shoot Game");
        Shoot game = new Shoot();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // 辅助结构体
    private static class Player {
        int x, y;
        boolean active;

        Player() {
            this.active = true;
        }
    }

    private static class Obstacle {
        int x, y;
        int width = OBSTACLE_WIDTH;
        int height = OBSTACLE_HEIGHT;
        int speedX, speedY;
        boolean active;

        Obstacle() {
            initSpeed();
        }

        void initActive() {
            this.x = (int) (Math.random() * 700 + 50);
            this.y = (int) (Math.random() * 500 + 50);
            this.active = true;
        }

        void initSpeed() {
            this.speedX = ((Math.random() < 0.5) ? -1 : 1) * (int) (Math.random() * 3 + 1);
            this.speedY = ((Math.random() < 0.5) ? -1 : 1) * (int) (Math.random() * 3 + 1);
        }
    }

    private static class Bullet {
        int x, y;
        double dx, dy;
        boolean active;
    }

    private static class EnemyBullet {
        int x, y;
        double dx, dy;
        boolean active;

        void init(int ox, int oy, int px, int py) {
            this.x = ox;
            this.y = oy;
            double vx = px - ox;
            double vy = py - oy;
            double len = Math.sqrt(vx * vx + vy * vy);
            if (len > 0.1) {
                this.dx = vx / len;
                this.dy = vy / len;
                this.active = true;
            }
        }
    }

    private static class TargetInfo {
        int idx, dist;

        TargetInfo(int idx, int dist) {
            this.idx = idx;
            this.dist = dist;
        }
    }
}
