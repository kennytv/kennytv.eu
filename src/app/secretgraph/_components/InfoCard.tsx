export default function InfoCard() {
  return (
    <div className="card p-4 md:p-6">
      <h2 className="mb-3 text-lg font-semibold text-text">About this data</h2>
      <div className="space-y-3 text-sm text-text-muted md:text-base">
        <p>
          Please don&apos;t take these numbers as gospel. bStats pages of plugins that have
          almost all of their users on modern versions will give you a better picture of
          concrete percentages, such as{' '}
          <a
            href="https://bstats.org/plugin/bukkit/Chunky/8211"
            target="_blank"
            rel="noopener"
          >
            Chunky
          </a>{' '}
          or{' '}
          <a
            href="https://bstats.org/plugin/bukkit/Multiverse-Core/7765"
            target="_blank"
            rel="noopener"
          >
            Multiverse
          </a>
          , with Spigot at (less than) 5%, with global stats below 10%.
        </p>
        <hr className="border-border" />
        <p>
          One weird spike is from bStats breaking, the other two are ???, the month of stale
          data is a month of me not realizing the cronjob was broken.
        </p>
        <p>
          Negative numbers come from Paper also reporting numbers without plugins and bStats
          API being only half useful - but the tendency remains correct.
        </p>
      </div>
    </div>
  );
}
